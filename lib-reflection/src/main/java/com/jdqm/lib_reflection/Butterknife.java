package com.jdqm.lib_reflection;

import android.app.Activity;

import com.jdqm.annotation.BindView;

import java.lang.reflect.Field;

public class Butterknife {

    public static void bind(Activity activity) {
        Class clazz = activity.getClass();
        Field[] declaredFields = clazz.getDeclaredFields();
        for (Field field: declaredFields) {
            BindView annotation = field.getAnnotation(BindView.class);
            if (annotation != null) {
                try {
                    field.setAccessible(true);
                    field.set(activity, activity.findViewById(annotation.value()));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }
            }
        }
    }
 }
