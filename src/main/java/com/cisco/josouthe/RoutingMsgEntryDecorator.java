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

    IReflector readIndex, readString, readFieldOrderIfDiff, readNull, readInt, skipBytes, pos;

    IReflector getDecoderWithoutResolver, getBackupDecoder, configure;

    public RoutingMsgEntryDecorator() {
        super();
        getLogger().info(String.format("Initializing %s, version %s, author %s, build date: %s", this.getClass().getCanonicalName(), MetaData.VERSION, MetaData.GECOS, MetaData.BUILDTIMESTAMP));

        readIndex = getNewReflectionBuilder().invokeInstanceMethod("readIndex", true).build(); //Integer if!=1 then no data left to read
        readString = getNewReflectionBuilder().invokeInstanceMethod("readString", true).build(); //String
        readFieldOrderIfDiff = getNewReflectionBuilder().invokeInstanceMethod("readFieldOrderIfDiff", true).build(); //Schema.Field[]
        readNull = getNewReflectionBuilder().invokeInstanceMethod("readNull", true).build(); //void
        readInt = getNewReflectionBuilder().invokeInstanceMethod("readInt", true).build(); //int
        skipBytes = getNewReflectionBuilder().invokeInstanceMethod("skipBytes", true).build(); //Bytes
        pos = getNewReflectionBuilder().invokeInstanceMethod("pos", true).build(); // int

        getDecoderWithoutResolver = getNewReflectionBuilder().accessFieldValue("in", true).build(); //returns a org.apache.avro.io.Decoder

        getBackupDecoder = getNewReflectionBuilder().accessFieldValue("backup", true).build(); //returns a org.apache.avro.io.Decoder
        configure = getNewReflectionBuilder().invokeInstanceMethod("configure", true, new String[]{ "org.apache.avro.io.Decoder" }).build(); //resets the input
    }

    @Override
    public String unmarshalTransactionContext(Object invokedObject, String className, String methodName, Object[] paramValues, ISDKUserContext context) throws ReflectorException {
        Object resolvingDecoder = paramValues[0]; // https://avro.apache.org/docs/1.9.2/api/java/org/apache/avro/io/ResolvingDecoder.html
        long startTimestamp = System.currentTimeMillis();

        Object decoder = getDecoderWithoutResolver.execute(invokedObject.getClass().getClassLoader(), resolvingDecoder);

        /*
        try {
            Object[] fieldOrder = (Object[]) readFieldOrderIfDiff.execute(invokedObject.getClass().getClassLoader(), resolvingDecoder);
            Integer messageId = (Integer) readInt.execute(invokedObject.getClass().getClassLoader(), decoder);
            Integer schema = null;
            if( fieldOrder == null ) {
                if( readIndex(decoder) != 1 ) {
                    readNull(decoder);
                } else {
                    schema = readInt(decoder);
                }
                if( readIndex(decoder) != 1 ) {
                    readNull(decoder);
                } else {
                    skipBytes.execute(invokedObject.getClass().getClassLoader(), decoder); //we are going to skip reading the message, since we don't want to use it
                }
            } else {
                for(int i=0; i<3; i++)
                    switch ( (Integer) pos.execute(invokedObject.getClass().getClassLoader(), fieldOrder[i]) ) {
                        case 0: { break; } //already read the message id above
                        case 1: {
                            if( readIndex(decoder) != 1 ) {
                                readNull(decoder);
                            } else {
                                schema = readInt(decoder);
                            }
                            break;
                        }
                        case 2: {
                            if( readIndex(decoder) != 1 ) {
                                readNull(decoder);
                            } else {
                                skipBytes.execute(invokedObject.getClass().getClassLoader(), decoder); //we are going to skip reading the message, since we don't want to use it
                            }
                            break;
                        }
                    }
            }
            context.stashObject("messageId", messageId);
            context.stashObject("schema", schema);
        } catch ( Exception reflectorException ) {
            getLogger().info(String.format("Oops, while trying to use a ResolvingDecoder, we had an exception, '%s'",reflectorException), reflectorException);
            restoreBackup(resolvingDecoder);
            throw reflectorException;
        }
         */

        String transactionContext = null;
        Integer indexValue;
        try {
            indexValue = (Integer) readIndex.execute(invokedObject.getClass().getClassLoader(), decoder);
            if (indexValue == 99) {
                transactionContext = (String) readString.execute(invokedObject.getClass().getClassLoader(), decoder);
            } else {
                restoreBackup(resolvingDecoder);
            }
        } catch( ReflectorException reflectorException ) {
            getLogger().info(String.format("Oops, while trying to read a correlation header, we had an exception, '%s'",reflectorException), reflectorException);
            restoreBackup(resolvingDecoder);
            throw reflectorException;
        }

        getLogger().info(String.format("We found a RoutingMsg tried to get correlation string, got '%s' from index value %d, this took %d ms", transactionContext, indexValue, System.currentTimeMillis()-startTimestamp));
        return transactionContext;
    }

    private void restoreBackup(Object resolvingDecoder) throws ReflectorException {
        try {
            Object backupDecoder = getBackupDecoder.execute(resolvingDecoder.getClass().getClassLoader(), resolvingDecoder);
            configure.execute(resolvingDecoder.getClass().getClassLoader(), resolvingDecoder, new Object[]{ backupDecoder });
        } catch ( ReflectorException reflectorException ) {
            getLogger().info(String.format("Oops, while trying to reset the internal decoder, we had an exception, '%s'",reflectorException), reflectorException);
            throw reflectorException;
        }
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
