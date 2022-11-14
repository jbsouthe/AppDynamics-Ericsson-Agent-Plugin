package com.cisco.josouthe;

import com.appdynamics.instrumentation.sdk.Rule;
import com.appdynamics.instrumentation.sdk.SDKClassMatchType;
import com.appdynamics.instrumentation.sdk.contexts.ISDKUserContext;
import com.appdynamics.instrumentation.sdk.template.AExit;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.IReflector;
import com.appdynamics.instrumentation.sdk.toolbox.reflection.ReflectorException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RoutingMsgExitDecorator extends AExit {

    IReflector writeIndex, writeString;

    public RoutingMsgExitDecorator() {
        super();
        getLogger().info(String.format("Initializing %s, version %s, author %s, build date: %s", this.getClass().getCanonicalName(), MetaData.VERSION, MetaData.GECOS, MetaData.BUILDTIMESTAMP));

        writeIndex = getNewReflectionBuilder().invokeInstanceMethod("writeIndex", true, new String[]{int.class.getCanonicalName()}).build(); //param[0]=integer (1) returns void
        writeString = getNewReflectionBuilder().invokeInstanceMethod("writeString", true, new String[]{String.class.getCanonicalName()}).build(); //param[0]=String (singularityHeader) returns void
    }

    //transactionContext is the correlation string to embed in the outbound message
    @Override
    public void marshalTransactionContext(String transactionContext, Object invokedObject, String className,
            String methodName, Object[] paramValues, Throwable thrownException,
            Object returnValue, ISDKUserContext context) {
        Object encoder = paramValues[0]; // https://avro.apache.org/docs/1.9.2/api/java/org/apache/avro/io/Encoder.html
        try {
            writeIndex.execute(invokedObject.getClass().getClassLoader(), encoder, new Object[] { 99 });
            writeString.execute(invokedObject.getClass().getClassLoader(), encoder, new Object[] { transactionContext });
            getLogger().info(String.format("Succeeded in writing correlation string '%s' to encoder", transactionContext));
        } catch (ReflectorException e) {
            getLogger().info(String.format("Exception when trying to write correlation string '%s' to encoder, Exception: '%s'", transactionContext, e),e);
        }
    }

    @Override
    public Map<String, String> identifyBackend(Object invokedObject, String className, String methodName,
            Object[] paramValues, Throwable thrownException,
            Object returnValue, ISDKUserContext context) {
        return null;
    }

    @Override
    public boolean isCorrelationEnabled() {
        return true;
    }

    @Override
    public boolean isCorrelationEnabledForOnMethodBegin() {
        return false;
    }

    @Override
    public List<Rule> initializeRules() {
        List<Rule> rules = new ArrayList<Rule>();
        rules.add(new Rule.Builder("com.ericsson.bss.rm.charging.routing.schemas.generated.RoutingMsg")
                .classMatchType(SDKClassMatchType.MATCHES_CLASS)
                .methodMatchString("customEncode").build());

        return rules;
    }

}
