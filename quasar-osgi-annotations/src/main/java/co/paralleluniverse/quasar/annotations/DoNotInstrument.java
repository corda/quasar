package co.paralleluniverse.quasar.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;

@Documented
@Target({ METHOD, TYPE })
@Retention(CLASS)
public @interface DoNotInstrument {
}
