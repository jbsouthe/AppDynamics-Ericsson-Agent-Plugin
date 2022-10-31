import javassist.*;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.ArrayList;

public class CorrelationTransformer implements ClassFileTransformer {
    ArrayList<String> whiteList;
    ArrayList<String> blackList;

    public CorrelationTransformer( String white, String black ) {
        whiteList = new ArrayList<>();
        blackList = new ArrayList<>();

        if( white != null ) for( String s : white.split(",") ) whiteList.add(s);
        if( black != null ) for( String s : black.split(",") ) blackList.add(s);
    }

    @Override
    public byte[] transform(ClassLoader loader,
            String className,
            Class<?> classBeingRedefined,
            ProtectionDomain protectionDomain,
            byte[] classfileBuffer) throws IllegalClassFormatException {
        //first make sure this class is in the list of classes to inject
        if( !isClassInList(className) ) {return classfileBuffer; }
        try {
            //initialize javassist
            ClassPool cp = ClassPool.getDefault();
            CtClass clazz = cp.get(className);
            boolean isSerializable = false;
            for( CtClass inter: clazz.getInterfaces() ) {
                if( inter.getName().equals("java.io.Serializable") ) isSerializable=true;
            }
            if( !isSerializable ) return classfileBuffer;

            CtField field = new CtField(cp.get("java.lang.String"), "appDynamicsCustomCorrelationString", clazz);
            field.setModifiers( Modifier.PRIVATE );
            clazz.addField( field );
            clazz.addMethod( CtNewMethod.getter("getAppDynamicsCustomCorrelationString", field) );
            clazz.addMethod( CtNewMethod.setter("setAppDynamicsCustomCorrelationString", field) );

            //return the modified class
            return clazz.toBytecode();
        } catch (Exception ex) { // ignored, don’t do this at home kids
            System.out.println("Exception in transform: "+ ex);
            ex.printStackTrace();
        }
        return classfileBuffer;
    }

    private boolean isClassInList( String s ) {
        if( blackList.contains(s) ) return false;
        if( whiteList.contains(s) ) return true;
        return false;
    }
}
