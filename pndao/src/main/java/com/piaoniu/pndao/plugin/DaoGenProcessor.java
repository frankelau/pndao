package com.piaoniu.pndao.plugin;

import com.google.common.collect.ImmutableSet;
import com.piaoniu.pndao.annotations.DaoGen;
import com.piaoniu.pndao.generator.dao.DaoEnv;
import com.piaoniu.pndao.generator.dao.MapperMethod;
import com.piaoniu.pndao.utils.DaoGenHelper;
import com.piaoniu.pndao.utils.ResourceHelper;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.util.Context;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.StandardLocation;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@SupportedAnnotationTypes("com.piaoniu.pndao.annotations.DaoGen")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class DaoGenProcessor  extends AbstractProcessor {
    public static final String PATH = DaoGen.class.getCanonicalName();

    // 工具实例类，用于将CompilerAPI, CompilerTreeAPI和AnnotationProcessing框架粘合起来
    private Trees trees;
    // 分析过程中可用的日志、信息打印工具
    private Messager messager;
    private Filer filer;
    private DaoGenHelper daoGenHelper;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.trees = Trees.instance(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        daoGenHelper = new DaoGenHelper(trees,context);
    }

    public void handle(Element element) {
        if (! (element.getKind() == ElementKind.INTERFACE)) return;
        Symbol.ClassSymbol classSymbol = (Symbol.ClassSymbol) element;
        String qualifiedName = classSymbol.getQualifiedName().toString();
        String clz = classSymbol.getSimpleName().toString();
        String pkg = qualifiedName.substring(0, qualifiedName.lastIndexOf("."));
        genXmlConfig(pkg, clz, classSymbol);
    }


    private void genXmlConfig(String pkg, String clz, Symbol.ClassSymbol classSymbol) {
        ResourceHelper.doWithOriAndPrintWriter(filer,StandardLocation.CLASS_OUTPUT, pkg, clz + ".xml",
                (data, writer) -> {
                    String newXml = genNewXml(data, classSymbol);
                    writer.print(newXml);
                    writer.flush();
                }
        );
    }
    private static Set<String> methodsInObjects = ImmutableSet.<String>builder().add(
     "finalize",
     "wait",
     "notifyAll",
     "notify",
     "toString",
     "clone",
     "equals",
     "hashCode",
     "getClass",
     "registerNatives").build();

    private String genNewXml(String data, Symbol.ClassSymbol classSymbol) {
        DaoGen daoGen = classSymbol.getAnnotation(DaoGen.class);
        DaoEnv daoEnv = new DaoEnv(daoGen,classSymbol);
        Function<Symbol.MethodSymbol,MapperMethod> gen = (methodSymbol -> DaoGenHelper.toMapperMethod(daoEnv, methodSymbol));

        Map<String,MapperMethod> methodMap =
                daoGenHelper.getMember(Symbol.MethodSymbol.class, ElementKind.METHOD, classSymbol)
                .stream()
                .filter(methodSymbol1 -> !methodsInObjects.contains(methodSymbol1.getSimpleName().toString()))
                .filter(m -> m.getAnnotationMirrors()
                        .stream()
                        .noneMatch(c -> c.getAnnotationType().toString().contains("org.apache.ibatis.annotations")))
                        .collect(Collectors.toMap(DaoGenHelper::getMethodName, gen));
        return daoGenHelper.mixMethodToData(daoGen, classSymbol.toString(),methodMap,data);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        annotations.stream()
                .filter(typeElement -> typeElement.toString().equals(PATH))
                .forEach(typeElement -> roundEnv.getElementsAnnotatedWith(typeElement)
                        .forEach((this::handle)));
        return true;
    }
}
