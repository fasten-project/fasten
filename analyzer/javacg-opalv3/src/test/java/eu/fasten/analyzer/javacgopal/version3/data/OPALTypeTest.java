package eu.fasten.analyzer.javacgopal.version3.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import eu.fasten.core.data.FastenURI;
import java.util.ArrayList;
import java.util.HashMap;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opalj.br.BaseType;
import org.opalj.br.ClassFile;
import org.opalj.br.ClassHierarchy;
import org.opalj.br.Code;
import org.opalj.br.DeclaredMethod;
import org.opalj.br.FieldType;
import org.opalj.br.Method;
import org.opalj.br.MethodDescriptor;
import org.opalj.br.ObjectType;
import org.opalj.br.instructions.Instruction;
import org.opalj.collection.QualifiedCollection;
import org.opalj.collection.immutable.$colon$amp$colon;
import org.opalj.collection.immutable.Chain;
import org.opalj.collection.immutable.RefArray;
import org.opalj.collection.immutable.UIDSet;
import org.opalj.collection.immutable.UIDSet1;
import scala.Option;

class OPALTypeTest {

    @Test
    void constructorTest() {
        var method = Mockito.mock(Method.class);
        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        var superClass = Mockito.mock(ObjectType.class);
        var superClasses = new $colon$amp$colon<>(superClass, Chain.empty());

        var superInterface = Mockito.mock(ObjectType.class);
        var superInterfaces = new ArrayList<ObjectType>();
        superInterfaces.add(superInterface);

        var type = new OPALType(methods, superClasses, superInterfaces, "source.java");

        assertEquals("source.java", type.getSourceFileName());
        assertEquals(123, type.getMethods().get(method));
        assertEquals(superClass, type.getSuperClasses().head());
        assertEquals(superInterface, type.getSuperInterfaces().get(0));
    }

    @Test
    void getType() {
    }

    @Test
    void getTypeNoSuperClassesNoSuperInterfaces() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        var type = Mockito.mock(ObjectType.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        var method = createMethod();
        Mockito.when(method.isPrivate()).thenReturn(true);

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        var opalType = new OPALType(methods, null, new ArrayList<>(), "source.java");

        var resultType = OPALType.getType(opalType, type);

        assertNotNull(resultType);
        assertNotNull(resultType.get(FastenURI.create("/some.package/typeName")));

        var ERCGType = resultType.get(FastenURI.create("/some.package/typeName"));
        assertEquals(1, resultType.size());
        assertEquals(1, ERCGType.getMethods().size());
        assertEquals("source.java", ERCGType.getSourceFileName());
        assertEquals(0, ERCGType.getSuperClasses().size());
        assertEquals(0, ERCGType.getSuperInterfaces().size());
        assertNotNull(ERCGType.getMethods().get(123));

        var node = ERCGType.getMethods().get(123);
        assertEquals(FastenURI.create("/some.package/typeName.methodName(typeName)typeName"),
                node.getUri());
        assertEquals(4, node.getMetadata().size());
        assertEquals(10, node.getMetadata().get("first"));
        assertEquals(30, node.getMetadata().get("last"));
        assertEquals(true, node.getMetadata().get("defined"));
        assertEquals("private", node.getMetadata().get("access"));
    }

    @Test
    void toURIDeclaredMethods() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        var type = Mockito.mock(ObjectType.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        var arrayOfParameters = new RefArray<FieldType>(new FieldType[]{type});

        var descriptor = Mockito.mock(MethodDescriptor.class);
        Mockito.when(descriptor.parameterTypes()).thenReturn(arrayOfParameters);
        Mockito.when(descriptor.returnType()).thenReturn(type);

        var declaredMethod = Mockito.mock(DeclaredMethod.class);
        Mockito.when(declaredMethod.descriptor()).thenReturn(descriptor);
        Mockito.when(declaredMethod.name()).thenReturn("methodName");
        Mockito.when(declaredMethod.declaringClassType()).thenReturn(type);

        var methods = new HashMap<DeclaredMethod, Integer>();
        methods.put(declaredMethod, 123);

        assertNotNull(OPALType.toURIDeclaredMethods(methods).get(123));
        assertEquals(0,
                OPALType.toURIDeclaredMethods(methods).get(123).getMetadata().size());
        assertEquals(FastenURI.create("/some.package/typeName.methodName(typeName)typeName"),
                OPALType.toURIDeclaredMethods(methods).get(123).getUri());
    }

    @Test
    void toURIInterfaces() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        var type = Mockito.mock(ObjectType.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        var list = new ArrayList<ObjectType>();
        list.add(type);

        assertEquals(1, OPALType.toURIInterfaces(list).size());
        assertEquals(FastenURI.create("/some.package/typeName"),
                OPALType.toURIInterfaces(list).get(0));
    }

    @Test
    void toURIClasses() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        var type = Mockito.mock(ObjectType.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        var chain = new $colon$amp$colon<>(type, Chain.empty());

        assertEquals(1, OPALType.toURIClasses(chain).size());
        assertEquals(FastenURI.create("/some.package/typeName"),
                OPALType.toURIClasses(chain).get(0));
    }

    @Test
    void toURIMethodsPrivateWithDefinedLines() {
        var method = createMethod();

        Mockito.when(method.isPrivate()).thenReturn(true);

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        assertNotNull(OPALType.toURIMethods(methods));
        assertNotNull(OPALType.toURIMethods(methods).get(123));

        var node = OPALType.toURIMethods(methods).get(123);
        assertEquals(FastenURI.create("/some.package/typeName.methodName(typeName)typeName"),
                node.getUri());
        assertEquals(4, node.getMetadata().size());
        assertEquals(10, node.getMetadata().get("first"));
        assertEquals(30, node.getMetadata().get("last"));
        assertEquals(true, node.getMetadata().get("defined"));
        assertEquals("private", node.getMetadata().get("access"));
    }

    @Test
    void toURIMethodsPublicWithEmptyLines() {
        var method = createMethod();

        Mockito.when(method.isPublic()).thenReturn(true);
        Mockito.when(method.body().get().firstLineNumber()).thenReturn(Option.empty());
        Mockito.when(method.body().get().lineNumber(20)).thenReturn(Option.empty());

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        assertNotNull(OPALType.toURIMethods(methods));
        assertNotNull(OPALType.toURIMethods(methods).get(123));

        var node = OPALType.toURIMethods(methods).get(123);

        Mockito.when(method.instructionsOption()).thenReturn(Option.empty());

        node = OPALType.toURIMethods(methods).get(123);
        assertEquals("", node.getMetadata().get("first"));
        assertEquals("", node.getMetadata().get("last"));
        assertEquals(false, node.getMetadata().get("defined"));
        assertEquals("public", node.getMetadata().get("access"));
    }

    @Test
    void toURIMethodsPackagePrivateWithNotFoundLines() {
        var method = createMethod();

        Mockito.when(method.isPackagePrivate()).thenReturn(true);
        Mockito.when(method.body()).thenReturn(Option.empty());

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        assertNotNull(OPALType.toURIMethods(methods));
        assertNotNull(OPALType.toURIMethods(methods).get(123));

        var node = OPALType.toURIMethods(methods).get(123);

        assertEquals("notFound", node.getMetadata().get("first"));
        assertEquals("notFound", node.getMetadata().get("last"));
        assertEquals("packagePrivate", node.getMetadata().get("access"));
    }

    @Test
    void toURIMethodsProtectedWithNotFoundLines() {
        var method = createMethod();

        Mockito.when(method.isProtected()).thenReturn(true);

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        assertNotNull(OPALType.toURIMethods(methods));
        assertNotNull(OPALType.toURIMethods(methods).get(123));

        var node = OPALType.toURIMethods(methods).get(123);

        assertEquals("protected", node.getMetadata().get("access"));
    }

    @Test
    void toURIMethodsNotFoundAccessWithNotFoundLines() {
        var method = createMethod();

        var methods = new HashMap<Method, Integer>();
        methods.put(method, 123);

        assertNotNull(OPALType.toURIMethods(methods));
        assertNotNull(OPALType.toURIMethods(methods).get(123));

        var node = OPALType.toURIMethods(methods).get(123);

        assertEquals("notFound", node.getMetadata().get("access"));
    }

    private Method createMethod() {
        var wrapperType = Mockito.mock(ObjectType.class);
        Mockito.when(wrapperType.packageName()).thenReturn("some/package");

        var baseType = Mockito.mock(BaseType.class);
        Mockito.when(baseType.WrapperType()).thenReturn(wrapperType);
        Mockito.when(baseType.toString()).thenReturn("typeName");

        var type = Mockito.mock(ObjectType.class);
        Mockito.when(type.asBaseType()).thenReturn(baseType);
        Mockito.when(type.isBaseType()).thenReturn(true);

        var arrayOfParameters = new RefArray<FieldType>(new FieldType[]{type});

        var descriptor = Mockito.mock(MethodDescriptor.class);
        Mockito.when(descriptor.parameterTypes()).thenReturn(arrayOfParameters);
        Mockito.when(descriptor.returnType()).thenReturn(type);

        var classFile = Mockito.mock(ClassFile.class);
        Mockito.when(classFile.thisType()).thenReturn(type);

        var code = Mockito.mock(Code.class);
        Mockito.when(code.firstLineNumber()).thenReturn(Option.apply(10));
        Mockito.when(code.lineNumber(20)).thenReturn(Option.apply(30));
        Mockito.when(code.codeSize()).thenReturn(20);

        var method = Mockito.mock(Method.class);
        Mockito.when(method.descriptor()).thenReturn(descriptor);
        Mockito.when(method.name()).thenReturn("methodName");
        Mockito.when(method.declaringClassFile()).thenReturn(classFile);
        Mockito.when(method.body()).thenReturn(Option.apply(code));
        Mockito.when(method.instructionsOption()).thenReturn(Option.apply(new Instruction[]{}));

        return method;
    }

    @Test
    void extractSuperClassesCorrect() {
        var superClass = Mockito.mock(ObjectType.class);
        var currentType = Mockito.mock(ObjectType.class);
        var chain = new $colon$amp$colon<>(superClass, Chain.empty());

        var qualifiedCollection = Mockito.mock(QualifiedCollection.class);
        Mockito.when(qualifiedCollection.s()).thenReturn(chain);

        var uidSet = Mockito.mock(UIDSet.class);
        Mockito.when(uidSet.nonEmpty()).thenReturn(true);

        var classHierarchy = Mockito.mock(ClassHierarchy.class);
        Mockito.when(classHierarchy.supertypes(currentType)).thenReturn(uidSet);
        Mockito.when(classHierarchy.allSuperclassTypesInInitializationOrder(currentType))
                .thenReturn(qualifiedCollection);

        assertEquals(superClass, OPALType.extractSuperClasses(classHierarchy, currentType).head());
    }

    @Test
    void extractSuperClassesEmptySuperClasses() {
        var currentType = Mockito.mock(ObjectType.class);

        var uidSet = Mockito.mock(UIDSet.class);
        Mockito.when(uidSet.nonEmpty()).thenReturn(false);

        var classHierarchy = Mockito.mock(ClassHierarchy.class);
        Mockito.when(classHierarchy.supertypes(currentType)).thenReturn(uidSet);

        assertNull(OPALType.extractSuperClasses(classHierarchy, currentType));
    }

    @Test
    void extractSuperClassesNullSuperClasses() {
        var currentType = Mockito.mock(ObjectType.class);

        var qualifiedCollection = Mockito.mock(QualifiedCollection.class);
        Mockito.when(qualifiedCollection.s()).thenReturn(null);

        var uidSet = Mockito.mock(UIDSet.class);
        Mockito.when(uidSet.nonEmpty()).thenReturn(true);

        var classHierarchy = Mockito.mock(ClassHierarchy.class);
        Mockito.when(classHierarchy.supertypes(currentType)).thenReturn(uidSet);
        Mockito.when(classHierarchy.allSuperclassTypesInInitializationOrder(currentType))
                .thenReturn(qualifiedCollection);

        assertNull(OPALType.extractSuperClasses(classHierarchy, currentType));
    }

    @Test
    void extractSuperInterfaces() {
        var superInterface = Mockito.mock(ObjectType.class);
        var currentType = Mockito.mock(ObjectType.class);

        var uidSet = new UIDSet1<>(superInterface);

        var classHierarchy = Mockito.mock(ClassHierarchy.class);
        Mockito.when(classHierarchy.allSuperinterfacetypes(currentType, false))
                .thenReturn(uidSet);

        assertEquals(1,
                OPALType.extractSuperInterfaces(classHierarchy, currentType).size());
        assertEquals(superInterface,
                OPALType.extractSuperInterfaces(classHierarchy, currentType).get(0));
    }
}