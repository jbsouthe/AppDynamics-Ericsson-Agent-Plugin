package com.cisco.josouthe;

import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.contexts.ISDKUserContext;
import com.appdynamics.instrumentation.sdk.template.AEntry;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;

import java.util.ArrayList;
import java.util.List;

public class RoutingMsgEntryDecorator extends AEntry {

    IReflector readIndex, readString, clone, readFieldOrderIfDiff, readNull, readInt, skipBytes, pos;

    public RoutingMsgEntryDecorator() {
        super();

        readIndex = getNewReflectionBuilder().invokeInstanceMethod("readIndex", true).build(); //Integer if!=1 then no data left to read
        readString = getNewReflectionBuilder().invokeInstanceMethod("readString", true).build(); //String
        clone = getNewReflectionBuilder().invokeInstanceMethod("clone", true).build(); // Object if this does not work use the Builder(RoutingMsg other) method
        readFieldOrderIfDiff = getNewReflectionBuilder().invokeInstanceMethod("readFieldOrderIfDiff", true).build(); //Schema.Field[]
        readNull = getNewReflectionBuilder().invokeInstanceMethod("readNull", true).build(); //void
        readInt = getNewReflectionBuilder().invokeInstanceMethod("readInt", true).build(); //int
        skipBytes = getNewReflectionBuilder().invokeInstanceMethod("skipBytes", true).build(); //Bytes
        pos = getNewReflectionBuilder().invokeInstanceMethod("pos", true).build(); // int
    }

    //This language "unmarshalTransactionContext returns a string value of the singularityHeader
    @Override
    public String unmarshalTransactionContext(Object invokedObject, String className, String methodName, Object[] paramValues, ISDKUserContext context) throws ReflectorException {
        Object resolvingDecoder = clone.execute(invokedObject.getClass().getClassLoader(), paramValues[0]); // https://avro.apache.org/docs/1.9.2/api/java/org/apache/avro/io/ResolvingDecoder.html

        //cloned the Resolving Decoder before the method runs, hoping that means we will have content we can read independent of the parameter in the cloned object
        try {
            Object[] fieldOrder = (Object[]) readFieldOrderIfDiff.execute(invokedObject.getClass().getClassLoader(), readFieldOrderIfDiff);
            Integer messageId = (Integer) readInt.execute(invokedObject.getClass().getClassLoader(), resolvingDecoder);
            Integer schema = null;
            if( fieldOrder == null ) {
                if( readIndex(resolvingDecoder) != 1 ) {
                    readNull(resolvingDecoder);
                } else {
                    schema = readInt(resolvingDecoder);
                }
                if( readIndex(resolvingDecoder) != 1 ) {
                    readNull(resolvingDecoder);
                } else {
                    skipBytes.execute(invokedObject.getClass().getClassLoader(), resolvingDecoder); //we are going to skip reading the message, since we don't want to use it
                }
            } else { //read using the field order; just copying logic, not sure how the message comes in, order should be maintained as per javadoc, but YMMV
                for(int i=0; i<3; i++)
                    switch ( (Integer) pos.execute(invokedObject.getClass().getClassLoader(), fieldOrder[i]) ) {
                        case 0: { break; } //already read the message id above
                        case 1: {
                            if( readIndex(resolvingDecoder) != 1 ) {
                                readNull(resolvingDecoder);
                            } else {
                                schema = readInt(resolvingDecoder);
                            }
                            break;
                        }
                        case 2: {
                            if( readIndex(resolvingDecoder) != 1 ) {
                                readNull(resolvingDecoder);
                            } else {
                                skipBytes.execute(invokedObject.getClass().getClassLoader(), resolvingDecoder); //we are going to skip reading the message, since we don't want to use it
                            }
                            break;
                        }
                    }
            }
            context.stashObject("messageId", messageId);
            context.stashObject("schema", schema);
        } catch ( ReflectorException reflectorException ) {
            getLogger().info(String.format("Oops, while trying to use a cloned ResolvingDecoder, we had an exception, '%s'",reflectorException), reflectorException);
            throw reflectorException;
        }

        String transactionContext = null;
        Integer indexValue = (Integer) readIndex.execute(invokedObject.getClass().getClassLoader(), resolvingDecoder);
        if( indexValue == 1 ) {
            transactionContext = (String) readString.execute(invokedObject.getClass().getClassLoader(), resolvingDecoder);
        }
        getLogger().info(String.format("We found a RoutingMsg tried to get correlation string, got '%s' from index value %d", transactionContext, indexValue));
        return transactionContext;
    }

    private int readIndex(Object resolvingDecoder) throws ReflectorException {
        return (Integer) readIndex.execute(resolvingDecoder.getClass().getClassLoader(), resolvingDecoder);
    }

    private int readInt(Object resolvingDecoder ) throws ReflectorException {
        return (Integer) readInt.execute(resolvingDecoder.getClass().getClassLoader(), resolvingDecoder);
    }

    private void readNull(Object resolvingDecoder ) throws ReflectorException {
        readNull.execute(resolvingDecoder.getClass().getClassLoader(), resolvingDecoder);
    }


    @Override
    public String getBusinessTransactionName(Object invokedObject, String className, String methodName, Object[] paramValues, ISDKUserContext context) throws ReflectorException {
        Integer schema = (Integer) context.fetchObject("schema");
        return String.format("RoutingMsg-SchemaNum-%s", ( schema == null ? "NULL" : String.valueOf(schema)));
    }

    @Override
    public boolean isCorrelationEnabled() {
        return true;
    }

    @Override
    public boolean isCorrelationEnabledForOnMethodBegin() {
        return true;
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();
        rules.add(new Rule.Builder("com.ericsson.bss.rm.charging.routing.schemas.generated.RoutingMsg")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("customDecode").build());

        return rules;
    }

}
