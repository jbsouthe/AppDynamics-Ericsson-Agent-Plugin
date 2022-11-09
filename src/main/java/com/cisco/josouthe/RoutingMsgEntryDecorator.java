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

    //This language "unmarshalTransactionContext returns a string value of the singularityHeader
    @Override
    public String unmarshalTransactionContext(Object invokedObject, String className, String methodName, Object[] paramValues, ISDKUserContext context) throws ReflectorException {
        Object resolvingDecoder = paramValues[0]; // https://avro.apache.org/docs/1.9.2/api/java/org/apache/avro/io/ResolvingDecoder.html
        long startTimestamp = System.currentTimeMillis();
        //the avro resolving decoder makes a backup of the decoder and copies the backup over the decoder after it reaches the end, this is self rewinding, no clone needed!
        /*ie:
            if (top instanceof Symbol.DefaultStartAction) {
                Symbol.DefaultStartAction dsa = (Symbol.DefaultStartAction)top;
                this.backup = this.in;
                this.in = DecoderFactory.get().binaryDecoder(dsa.contents, null);
            } else if (top == Symbol.DEFAULT_END_ACTION) {
                this.in = this.backup;
            }
         */

        /* technique number 1 is to just use the resolving decoder and allow the method above to reset it after it is read,

        if that fails let's uncomment this next line and just use the decoder in the raw, which seems to skip schema validation:
        resolvingDecoder = getDecoderWithoutResolver.execute(invokedObject.getClass().getClassLoader(), resolvingDecoder);
        */


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
            getLogger().info(String.format("Oops, while trying to use a ResolvingDecoder, we had an exception, '%s'",reflectorException), reflectorException);
            throw reflectorException;
        }

        String transactionContext = null;
        Integer indexValue = (Integer) readIndex.execute(invokedObject.getClass().getClassLoader(), resolvingDecoder);
        if( indexValue == 1 ) {
            transactionContext = (String) readString.execute(invokedObject.getClass().getClassLoader(), resolvingDecoder);
        }

        /*if that also fails for any number of reasons, we can assume that the ResolvingDecoder is not resetting, confirms data read matches the schema and the read is destructive,
        this means we need to fall through all this and at the end of it we will need to use the builder to make a new decoder for the program to read.
        in reading the avro source, this is HIGHLY unlikely so we can assume all that is not needed.

        If the state of the decoder is "bad" let's uncomment this next few lines and allow the decoder to reset the input with the backup manually before we enter into this method
            MAKE SURE TO COMMENT OUT LINE 56, if you go this route as the internal decoder does not have a configure method
        try {
            Object backupDecoder = getBackupDecoder.execute(resolvingDecoder.getClass().getClassLoader(), resolvingDecoder);
            configure.execute(resolvingDecoder.getClass().getClassLoader(), resolvingDecoder, new Object[]{ backupDecoder });
        } catch ( ReflectorException reflectorException ) {
            getLogger().info(String.format("Oops, while trying to reset the internal decoder, we had an exception, '%s'",reflectorException), reflectorException);
            throw reflectorException;
        }
         */
        getLogger().info(String.format("We found a RoutingMsg tried to get correlation string, got '%s' from index value %d, this took %d ms", transactionContext, indexValue, System.currentTimeMillis()-startTimestamp));
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
