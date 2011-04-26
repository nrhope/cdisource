package org.cdisource.testing.junit;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

@Retention(RUNTIME) @Target({TYPE})
public @interface RunConfig {
	boolean runInRequestScope () default false;
}
