# Android-注解处理器-手写Butterknife

大家可能都用过Butterknife，今天就从0到1写一个Butterknife，首先通过反射的方式实现，接着再改为注解处理器实现，通过这个例子进而掌握注解处理器的使用。完整代码参考：[AnnotationProcessor][2]

其中也会涉及一些比较有用的知识：
1、注解相关，在运行时通过反射获取注解信息
2、通过 [javapoet][1] 生成代码，生成代码这个功能如果运用好了，可以解决很多繁琐的重复工作

先看一个示例代码：

```
public class MainActivity extends AppCompatActivity {
    @BindView(R.id.text_view1)
    TextView textView1;

    @BindView(R.id.text_view2)
    TextView textView2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

//        textView1 = findViewById(R.id.text_view1);
//        textView2 = findViewById(R.id.text_view2);
        Butterknife.bind(this);

        textView1.setText("Hello Annotation1");
        textView2.setText("Hello Annotation2");
    }
}
```

布局里面有两个TextView，分别是textView1和textView2，正常来说我们需要通过以下代码来获取这两个对象

```
textView1 = findViewById(R.id.text_view1);
textView2 = findViewById(R.id.text_view2);
```

现在我们用```Butterknife.bind(this)```加上反射注解的方式来实现。

首先我们需要新建一个注解：

```
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface BindView {
    int value();
}
```

```@Retentio``` ```@Target``` 是注解的注解，即元注解。

```@Retention``` 有三个值，分别是```SOURCE```、```CLASS```和```RUNTIME``` ，指定注解信息保留到哪个阶段，原码中、编译的字节码、或者运行时仍然保留，使用原则上应尽量早丢弃注解信息。这个例子需要在运行时通过反射拿到注解信息，因此指定为为```RetentionPolicy.RUNTIME```。

```@Target``` 指定注解可以修饰哪些元素，例如方法，成员变量，本地变量等，这个例子中需要修饰的是成员变量，即Field。


首先拿到Activity中声明的所有成员属性，然后判断该成员属性是否有```@BindView``` 注解修饰，如果有，就通过反射调用设置属性的值。

```
public class Butterknife {

    public static void bind(Activity activity) {
        Class clazz = activity.getClass();
        //拿到Activity中声明的所有成员属性
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field: declaredFields) {
            BindView annotation = field.getAnnotation(BindView.class);
            //成员属性是否有@BindView注解
            if (annotation != null) {
                try {
                    field.setAccessible(true);
                    //如果有注解，通过反射调用设置属性的值
                    field.set(activity, activity.findViewById(annotation.value()));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
 }
```

这个例子通过反射的方式，替我们写了findViewById()，但是我们知道，反射有一定的性能损耗，如果一个Activity中有几十个上百个View要查找，那反射带来的性能问题将不容忽视。方便是方便了，如果因此而引起比较大的性能问题，在工程实践上是不能够被接受的，因此我们需要寻找其他出路-注解处理器。


新建一个注解处理module（Java Library），它大概长这个样子。

![lib-processor](https://upload-images.jianshu.io/upload_images/3631399-347340005d47e51f.png?imageMogr2/auto-orient/strip%7CimageView2/2/w/1240)

注意一个文件```javax.annotation.processing.Processor``` ，它的目录是固定的，文件名也是固定的。```/src/main/resources/META-INF/services/javax.annotation.processing.Processor```，它的内容很简单，就是注解处理的类名

```
com.jdqm.lib.processor.BindingProcessor
```

来看看注解处理器类如何写。
>本例子中使用square出品的开源项目 [javapoet][1] 来生成代码，其API也比较简单易用，有兴趣可参考官方文档：[javapoet][1] 

```
public class BindingProcessor extends AbstractProcessor {
    private Filer filer;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);

        filer = processingEnv.getFiler();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnvironment) {

        for (Element element : roundEnvironment.getRootElements()) {
            String packageStr = element.getEnclosingElement().toString();
            String classStr = element.getSimpleName().toString();
            
            //要生成.java文件的类名：com.example.annotationprocessor.MainActivityBinding
            ClassName className = ClassName.get(packageStr, classStr + "Binding");
            
            //构造器 public MainActivityBinding(MainActivity activity
            MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                    .addModifiers(Modifier.PUBLIC)
                    .addParameter(ClassName.get(packageStr, classStr), "activity");
            
            //标记类中是否有@BindView注解修饰字段，如果没有就没必要生成类了
            boolean hasBinding = false;

            for (Element enclosedElement : element.getEnclosedElements()) {
                if (enclosedElement.getKind() == ElementKind.FIELD) {
                    BindView bindView = enclosedElement.getAnnotation(BindView.class);
                    if (bindView != null) {
                        hasBinding = true;
                        
                        //在构造器内添加代码段
                        // activity.textView1 = activity.findViewById(2131231096);
                        // activity.textView2 = activity.findViewById(2131231097);
                        constructorBuilder.addStatement("activity.$N = activity.findViewById($L)",
                                enclosedElement.getSimpleName(), bindView.value());
                    }
                }
            }

            //生成 com.example.annotationprocessor.MainActivityBinding 类
            TypeSpec builtClass = TypeSpec.classBuilder(className)
                    .addModifiers(Modifier.PUBLIC)
                    .addMethod(constructorBuilder.build())
                    .build();

            if (hasBinding) {
                try {
                    //生成 MainActivityBinding.java文件
                    JavaFile.builder(packageStr, builtClass)
                            .build().writeTo(filer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Collections.singleton(BindView.class.getCanonicalName());
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }
}
```

注意我们要回去原先的注解，将它的```@Retention``` 改为```RetentionPolicy.SOURCE```，因为注解处理器是在源码阶段工作，没必要将注解信息保留到字节码或者运行时。

然后我们需要在app/build.gradle中添加依赖注解处理器
```
annotationProcessor project(':lib-processor')
```

看看注解处理器生成的代码

```
public class MainActivityBinding {
  public MainActivityBinding(MainActivity activity) {
    //这里的id已经被替换为R文件中具体值
    activity.textView1 = activity.findViewById(2131231096);
    activity.textView2 = activity.findViewById(2131231097);
  }
}
```

接着修改一下```Butterknife.bind(this)```方法的实现

```
public class Butterknife {

    public static void bind(Activity activity) {
        try {
            //拼接成注解处理器中生产的类名
            String className = activity.getClass().getCanonicalName() + "Binding";
            
            //通过放射创建对象，此时会调用注解处理器生成类的构造方法，在构造方法中调用activity.findViewById(id)
            Class<?> clazz = Class.forName(className);
            Constructor<?> constructor = clazz.getDeclaredConstructor(activity.getClass());
            constructor.newInstance(activity);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }
 }
```

使用注解处理器后，每个```Butterknife.bind(this)```就只会涉及一次反射（不管对应的Activity有多少个View），一次的性能损耗是可以忽略的。

好了，今天的总结就到这，谢谢大家。

完整代码参考：[AnnotationProcessor][2]


[1]: https://github.com/square/javapoet
[2]: https://github.com/jdqm/AnnotationProcessor


