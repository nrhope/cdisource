package org.cdisource.springintegration;

import java.beans.Introspector;
import java.lang.reflect.Type;
import java.util.Set;

import javax.enterprise.inject.spi.Bean;
import javax.enterprise.inject.spi.InjectionPoint;
import javax.inject.Named;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import org.cdisource.logging.Logger;
import static org.cdisource.logging.LogFactoryManager.logger;


public class CdiBeanFactoryPostProcessor implements BeanFactoryPostProcessor {

  public static final String FACTORY_BEAN = "_FactoryBeanForCDIBean"; // @yarris : make name more obvious in logs than *FactoryBean
public static final String BEAN_LOG_PREFIX = "spring-cdisource: ";
  public static final String BEAN_REG_LOG_PREFIX = BEAN_LOG_PREFIX + "registering bean: from Spring => CDI ";
	
	private boolean useLongName;
	
	private static String 	beanNamePrefix = "producerBean"; // "edenDocConfig";

	private BeanManagerLocationUtil beanManagerLocationUtil = new BeanManagerLocationUtil();


	@Override
	public void postProcessBeanFactory(
			ConfigurableListableBeanFactory beanFactory) throws BeansException {
	  Logger logger = logger(CdiBeanFactoryPostProcessor.class);	
		DefaultListableBeanFactory factory = (DefaultListableBeanFactory) beanFactory;
		
		Set<Bean<?>> beans = beanManagerLocationUtil.beanManager().getBeans(Object.class); // get all CDI objects (e.g. from Weld)
		for (Bean<?> bean : beans) {
			final String		cdiBeanName = bean.getName();
			
			if (bean instanceof SpringIntegrationExtention.SpringBean) {
				logger.debug(BEAN_LOG_PREFIX + " skipping bean " + bean + " (instanceof SpringIntegrationExtention.SpringBean)");
				continue;
			}
			
			if (cdiBeanName!=null && cdiBeanName.equals("Spring Injection")){
				logger.debug(BEAN_LOG_PREFIX + " skipping bean " + bean + " (name='Spring Injection')");
				continue;
			}
			if (bean instanceof InjectionPoint) { // @TODO class org.jboss.weld.bean.ProducerMethod
				// Need special handling to register a String Producer called mailHost" for 
				// [[BackedAnnotatedMethod] @Produces @Named public org.cdisource.springintegration.ProducerBean.mailHost()]

			}
	        logger.debug("bean types = {}", bean.getTypes());
	        Class<?> beanClass = getBeanClass(bean);
			BeanDefinitionBuilder definition = BeanDefinitionBuilder.rootBeanDefinition(CdiFactoryBean.class)
						.addPropertyValue("beanClass", beanClass)
						.addPropertyValue("beanManager", beanManagerLocationUtil.beanManager())
						.addPropertyValue("qualifiers", bean.getQualifiers())
						.setLazyInit(true);
			
			BeanDefinition	existingSpringBeanDef = null;
			Object			existingSpringBean = null;
			Exception	ignoreEx = null;
			String		beanName = Introspector.decapitalize(bean.getBeanClass().getSimpleName()); // origName    Introspector.decapitalize(name);
			/* @TODO doesn't look for namedAnnotation in the correct place on
			 * bean=Producer Method [String] with qualifiers [@Default @Any @Named] declared as [[BackedAnnotatedMethod] @Produces @Named public org.cdisource.springintegration.ProducerBean.mailHost()]
			 * 		looks like maybe should look on annotatedMethod instead ...? or on producer.method annotations and also to get type for
			 * 		Spring bean registration
			 * 	cdiBeanName is correct="mailHost"
			 */
			Named 		namedAnnotation = (Named) bean.getBeanClass().getAnnotation(Named.class);
			String		namedAnnotationValue = null;
			String		lookupName = beanName;
			
			
			if ((namedAnnotation != null) && !"".equals(namedAnnotation.value())) {
				namedAnnotationValue = namedAnnotation.value();	// explict name, e.g. @Named("myBean"), so have to register it
				lookupName = namedAnnotationValue;
			}
			if (lookupName.toLowerCase().startsWith(beanNamePrefix.toLowerCase())) {
				lookupName = lookupName;
			}
			try {
				existingSpringBeanDef = factory.getBeanDefinition(lookupName);
			} catch (Exception e) {
				ignoreEx = e;
			}
			if (existingSpringBeanDef == null) {
				try {
					existingSpringBean = factory.getBean(lookupName);
//				existingSpringBean = factory.getBean(definition.getBeanDefinition().getBeanClass());
				/* hopefully we can avoid any Impl-specific nastiness in favour of above lookup(s)
				final Set<Type> types = bean.getTypes(); // e.g. "[interface au.com.hubbub.consol.beans.workflow.service.WorkflowService, class au.com.hubbub.consol.beans.workflow.service.AbstractWorkflowService, class au.com.hubbub.consol.beans.workflow.service.CreateOrderWorkflowService, class java.lang.Object]"
				if (bean instanceof org.jboss.weld.bean.ManagedBean) {
					org.jboss.weld.bean.ManagedBean	weldBean = (org.jboss.weld.bean.ManagedBean) bean;
					
					type = bean.getType();
					factory.containsBeanDefinition(beanName);
				}
				*/
				} catch (Exception e) {
					ignoreEx = e;
				}
			} else {
				existingSpringBean = existingSpringBeanDef;
			}
			if (lookupName.startsWith(beanNamePrefix)) {
				logger.severe(BEAN_REG_LOG_PREFIX + " lookupName=" + lookupName + " : bean=" + existingSpringBean + ", def=" + existingSpringBeanDef);
			}
			
			// @Named annotations are in javax. but both CDI and Spring seem to process them, leading to dups?
			
			
			if (existingSpringBean == null) {
				String		name = (namedAnnotationValue != null ? namedAnnotationValue : generateNameBasedOnClassName(bean));
				
//				String name = generateName(bean); // @yarris : get null here for CreateOrderWorkflowServiceFactoryBean
				logger.severe(BEAN_REG_LOG_PREFIX + " name=" + name + " (bean=" + beanClass + ") [exisiting_def=" + (existingSpringBeanDef != null) + "]");
				factory.registerBeanDefinition(name, definition.getBeanDefinition());
			} else {
				logger.severe(BEAN_LOG_PREFIX +  " skipping bean '" + lookupName +"' as already exists in Spring");
			}
		}
	}

  private Class<?> getBeanClass(Bean<?> bean) {
    Class<?> klass = Object.class;
    for (Type type : bean.getTypes()) {
      if (type instanceof Class) {
        Class<?> currentClass = (Class<?>) type;
        if (klass.isAssignableFrom(currentClass)) {
          klass = currentClass;
        }
      }
    }
    return klass;
  }

	/*
	private String generateName(Bean<?> bean) { // @yarris: moving logic into postProcessBeanFactory() main body as more complicated then this...
		Named named = (Named) bean.getBeanClass().getAnnotation(Named.class);
		String name = ((named != null) && !"".equals(named.value())) ? named.value() : generateNameBasedOnClassName(bean); // @yarris : avoid exception for plain "@Named" case, where value=""
		return name;
	}
	*/

	private String generateNameBasedOnClassName(Bean<?> bean) {
		return !useLongName ? bean.getBeanClass().getSimpleName() + FACTORY_BEAN : bean.getBeanClass().getName().replace(".", "_") + FACTORY_BEAN;
	}
	
	public void setUseLongName(boolean useLongName) {
		this.useLongName = useLongName;
	}


}
