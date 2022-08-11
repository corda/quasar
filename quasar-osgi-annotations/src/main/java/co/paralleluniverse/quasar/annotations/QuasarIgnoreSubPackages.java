package co.paralleluniverse.quasar.annotations;

import org.osgi.annotation.bundle.Header;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;
import static java.lang.annotation.ElementType.PACKAGE;

@Header(name = QuasarIgnorePackage.HEADER_NAME, value = "${@package}.**")
@Target({ ANNOTATION_TYPE, PACKAGE })
@Retention(CLASS)
public @interface QuasarIgnoreSubPackages {
}
