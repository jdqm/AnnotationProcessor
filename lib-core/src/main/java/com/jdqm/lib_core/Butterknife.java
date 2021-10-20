package com.jdqm.lib_core;

import android.app.Activity;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

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
