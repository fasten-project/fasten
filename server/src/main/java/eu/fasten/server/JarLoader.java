package eu.fasten.server;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * A utility class for loading an arbitrary Jar file.
 */
public class JarLoader {

    private URLClassLoader classLoader;

    JarLoader(String jarFile) throws MalformedURLException {
        File file = new File(jarFile);
        URL url = file.toURI().toURL();
        this.classLoader = new URLClassLoader(new URL[] {file.toURI().toURL()}, this.getClass().getClassLoader());
    }

    public Class loadClass(String classPath) throws ClassNotFoundException, NoSuchMethodException {
        return Class.forName(classPath, true, this.classLoader);
    }


    public static void main(String[] args) throws MalformedURLException, NoSuchMethodException, ClassNotFoundException, IllegalAccessException, InvocationTargetException, InstantiationException {

        // An example of how to load a Jar file and use its class and method.

        String jarPath = "/Users/amir/projects/fasten/analyzer/javacg-opal/target/javacg-opal-0.0.1-SNAPSHOT.jar";
        
        JarLoader jar = new JarLoader(jarPath);
        Class classToload = jar.loadClass("eu.fasten.analyzer.javacgopal.MavenCoordinate");
        Object instance = classToload.getDeclaredConstructor(new Class[]{String.class, String.class, String.class}).newInstance("A", "B", "C");

        Method method = classToload.getDeclaredMethod("getCoordinate", null);
        String result = method.invoke(instance).toString();

        System.out.println(result);

    }

}
