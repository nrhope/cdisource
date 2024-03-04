package org.cdisource.testing;

import javax.enterprise.inject.Produces;

@SomeOtherQualifier // @yarris, commenting out doesn't affect testLookupProducer() failure
public class InjectedBeanFactory {
    @Produces @SomeOtherQualifier InjectedBean createTransport() {
        return new InjectedBean();
    }
}
