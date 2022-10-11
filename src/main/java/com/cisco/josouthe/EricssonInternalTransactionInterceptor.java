package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.apm.appagent.api.DataScope;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.template.AGenericInterceptor;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class EricssonInternalTransactionInterceptor extends AGenericInterceptor {
    private static final ConcurrentHashMap<Object, TransactionDetail> transactionsMap = new ConcurrentHashMap<>();
    private final Scheduler scheduler;
    private final HashSet<DataScope> snapshotDatascopeOnly;
    private IReflector getFQId;

    public EricssonInternalTransactionInterceptor() {
        super();
        snapshotDatascopeOnly = new HashSet<DataScope>();
        snapshotDatascopeOnly.add(DataScope.SNAPSHOTS);
        scheduler = Scheduler.getInstance(10000L, 60000L, transactionsMap, getLogger());

        getFQId = getNewReflectionBuilder().invokeInstanceMethod( "getFQId", true).build();
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add( new Rule.Builder("com.ericsson.bss.rm.charging.access.runtime.transhandling.impl.InternalTransaction")
                .classMatchType(SDKClassMatchType.IMPLEMENTS_INTERFACE)
                .methodMatchString("<init>")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );

        rules.add( new Rule.Builder("com.ericsson.bss.rm.charging.access.runtime.transhandling.impl.InternalTransaction")
                .classMatchType(SDKClassMatchType.IMPLEMENTS_INTERFACE)
                .methodMatchString("run")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );
        return rules;
    }

    @Override
    public Object onMethodBegin(Object objectIntercepted, String className, String methodName, Object[] params) {
        getLogger().debug(String.format("onMethodBegin starting for %s %s.%s()", objectIntercepted.toString(), className, methodName));
        Transaction transaction = AppdynamicsAgent.getTransaction(); //naively grab an active BT on this thread, we expect this to be noop

        switch(methodName) {
            case "<init>": { //during init, constructor, we assume a BT is running, if not we start one, and then mark a handoff on this new object
                if( isFakeTransaction(transaction) ) { //this is a noop transaction, so we need to start a BT, one is not already running
                    transaction = AppdynamicsAgent.startTransaction("Transaction-placeholder", null, EntryTypes.POJO, true); //placeholder, we should try and configure a servlet bt for this transaction
                    getLogger().debug(String.format("Business Transaction was not running Transaction-placeholder(%s) started for %s.%s()", transaction.getUniqueIdentifier(), className, methodName));
                }

                if( isFakeTransaction(transaction) ) { //if the BT is still not started/real, we need to log it and abandon
                    getLogger().debug(String.format("Business Transaction is not running and could not be started for %s.%s()", className, methodName));
                    return null;
                }
                transaction.markHandoff(objectIntercepted); //this lets the agent know that we are handing off a segment to another thread of execution, which is what dispatch does sooner or later
                getLogger().debug(String.format("Transaction markHandoff initiated for guid: '%s' isAsync Flag: %s Common Object: %s", transaction.getUniqueIdentifier(), transaction.isAsyncTransaction(), objectIntercepted));

                transaction = AppdynamicsAgent.startSegment(objectIntercepted); //start a Segment of the BT that marked this object for handoff earlier

                if (isFakeTransaction(transaction)) { //this object was not marked for handoff? log it
                    getLogger().debug(String.format("We intercepted an implementation of an InternalTransaction that was not marked for handoff? %s %s.%s()", objectIntercepted, className, methodName));
                } else { //this is what we hope for, and means we are starting a segment of a BT after an async handoff
                    getLogger().debug(String.format("We intercepted an implementation of an InternalTransaction that was marked for handoff! %s transaction segment guid: %s, %s %s.%s()",  objectIntercepted, transaction.getUniqueIdentifier(), objectIntercepted, className, methodName));
                }
                transactionsMap.put(objectIntercepted, new TransactionDetail(objectIntercepted));
                break;
            }
            case "run": { //once start method is executed, we begin processing this coroutine, so we want to start the segment here and store it for the callback on finish

                transaction = AppdynamicsAgent.startSegment(objectIntercepted); //start a Segment of the BT that marked this object for handoff earlier
                String fqid = getReflectiveString(objectIntercepted, getFQId, "FQID-UNKNOWN");
                transaction.collectData("FQId", fqid, this.snapshotDatascopeOnly);
                if (isFakeTransaction(transaction)) { //this object was not marked for handoff? log it
                    getLogger().debug(String.format("We intercepted an implementation of an InternalTransaction that was not marked for handoff? %s %s.%s()", objectIntercepted, className, methodName));
                } else { //this is what we hope for, and means we are starting a segment of a BT after an async handoff
                    getLogger().debug(String.format("We intercepted an implementation of an InternalTransaction that was marked for handoff! transaction segment guid: %s, %s %s.%s()", transaction.getUniqueIdentifier(), objectIntercepted, className, methodName));
                }
                break;
            }
        }
        getLogger().debug(String.format("onMethodBegin ending for %s.%s()", className, methodName));
        return transaction;
    }

    @Override
    public void onMethodEnd(Object state, Object objectIntercepted, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        if( state == null ) return;
        Transaction transaction = (Transaction) state;
        if( exception != null ) {
            transaction.markAsError(exception.toString());
        }
        switch (methodName) {
            case "<init>": {
                //nothing to do here, yet
            }
            case "run": {
                transaction.endSegment();
                TransactionDetail transactionDetail = transactionsMap.get(objectIntercepted);
                if( transactionDetail != null ) {
                    transactionDetail.setFinished(true);
                }
                getLogger().debug(String.format("Made it to the end of an entire segment, and ended it"));
                break;
            }
        }
    }

    protected boolean isFakeTransaction(Transaction transaction) {
        return "".equals(transaction.getUniqueIdentifier());
    }
    protected String getReflectiveString(Object object, IReflector method, String defaultString) {
        String value = defaultString;
        if( object == null || method == null ) return defaultString;
        try{
            value = (String) method.execute(object.getClass().getClassLoader(), object);
            if( value == null ) return defaultString;
        } catch (ReflectorException e) {
            this.getLogger().info("Error in reflection call, exception: "+ e.getMessage(),e);
        }
        return value;
    }

    protected Integer getReflectiveInteger(Object object, IReflector method, Integer defaultInteger) {
        Integer value = defaultInteger;
        if( object == null || method == null ) return defaultInteger;
        try{
            value = (Integer) method.execute(object.getClass().getClassLoader(), object);
            if( value == null ) return defaultInteger;
        } catch (ReflectorException e) {
            this.getLogger().info("Error in reflection call, exception: "+ e.getMessage(),e);
        }
        return value;
    }

    protected Long getReflectiveLong( Object object, IReflector method ) {
        if( object == null || method == null ) return null;
        Object rawValue = getReflectiveObject( object, method );
        if( rawValue instanceof Long  ) return (Long) rawValue;
        if( rawValue instanceof Integer ) return ((Integer) rawValue).longValue();
        if( rawValue instanceof Double ) return ((Double)rawValue).longValue();
        if( rawValue instanceof Number ) return ((Number)rawValue).longValue();
        return null;
    }

    protected Object getReflectiveObject(Object object, IReflector method, Object... args) {
        Object value = null;
        if( object == null || method == null ) return value;
        try{
            if( args.length > 0 ) {
                value = method.execute(object.getClass().getClassLoader(), object, args);
            } else {
                value = method.execute(object.getClass().getClassLoader(), object);
            }
        } catch (ReflectorException e) {
            this.getLogger().info("Error in reflection call, method: "+ method.getClass().getCanonicalName() +" object: "+ object.getClass().getCanonicalName() +" exception: "+ e.getMessage(),e);
        }
        return value;
    }
}
