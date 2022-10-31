import java.lang.instrument.Instrumentation;

public class AutoCorrelationAgent {
    public static void premain(String args, Instrumentation instrumentation){
        instrumentation.addTransformer(new CorrelationTransformer(System.getProperty("appdynamics.correlation.whitelist"), System.getProperty("appdynamics.correlation.blacklist") ));
    }
}