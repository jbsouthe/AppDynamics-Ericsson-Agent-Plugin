package com.cisco.josouthe;

import com.appdynamics.agent.api.AppdynamicsAgent;
import com.appdynamics.agent.api.EntryTypes;
import com.appdynamics.agent.api.ExitCall;
import com.appdynamics.agent.api.ExitTypes;
import com.appdynamics.agent.api.Transaction;
import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.SDKStringMatchType;
import com.appdynamics.instrumentation.sdk.template.AGenericInterceptor;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RoutingMsgCorrelationHandler  extends AGenericInterceptor {

    private IReflector getAppDynamicsCustomCorrelationString, setAppDynamicsCustomCorrelationString;

    public RoutingMsgCorrelationHandler() {
        this.getAppDynamicsCustomCorrelationString = getNewReflectionBuilder().invokeInstanceMethod("getAppDynamicsCustomCorrelationString", true).build();
        this.setAppDynamicsCustomCorrelationString = getNewReflectionBuilder().invokeInstanceMethod("setAppDynamicsCustomCorrelationString", true).build();
    }

    @Override
    public Object onMethodBegin(Object objectIntercepted, String className, String methodName, Object[] params) {
        return null;
    }

    @Override
    public void onMethodEnd(Object state, Object objectIntercepted, String className, String methodName, Object[] params, Throwable exception, Object returnVal) {
        Transaction transaction = AppdynamicsAgent.getTransaction();
        if( isFakeTransaction(transaction) ) { //no BT active, start one with correlation, we will need to end this after we make sure this is working
            transaction = AppdynamicsAgent.startTransaction("RoutingMessage", getCorrelationHeader(objectIntercepted), EntryTypes.POJO, false);
            getLogger().info(String.format("Starting BT: '%s' will not be ending it, so we need to identify where to end this for next iteration of the code, these will show up after 5 minutes", transaction.getUniqueIdentifier()));
        } else { // BT is active, so start an exit call
            Map<String,String> map = new HashMap<>();
            ExitCall exitCall = transaction.startExitCall(map, "routerExitCall", ExitTypes.CUSTOM, true);
            getReflectiveObject(objectIntercepted, setAppDynamicsCustomCorrelationString, exitCall.getCorrelationHeader());
            getLogger().info(String.format("Starting Exit Call for BT(%s) with Correlation String: '%s'", transaction.getUniqueIdentifier(), exitCall.getCorrelationHeader()));
            exitCall.end();
        }
    }

    private String getCorrelationHeader( Object object ) {
        try {
            return (String) getAppDynamicsCustomCorrelationString.execute(object.getClass().getClassLoader(), object);
        } catch (ReflectorException e) { //not everything we see if going to have this method added to it, only jobs that have been prepared with our mini agent
            return null;
        }
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();

        rules.add( new Rule.Builder("com.ericsson.bss.rm.charging.routing.schemas.generated.RoutingMsg")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("<init>")
                .methodStringMatchType(SDKStringMatchType.EQUALS)
                .build()
        );
        return rules;
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
        if( object == null || method == null )
        {
            return value;
        }
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
