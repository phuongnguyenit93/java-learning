package com.example.learning.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
@Inherited
public @interface AnnotationExample {
    String [] stringParam() default {"12","34"};
    boolean isChecked() default true;
    int range() default 31;
    AnnoEnum annoEnum() default AnnoEnum.ENUM_1;

    enum AnnoEnum {
        ENUM_1,ENUM_2
    }
}
