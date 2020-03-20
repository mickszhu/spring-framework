package com.lin.temp;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.stereotype.Component;

/**
 * 程序员可以通过该方式参与spring的初始化
 * Create by ljc on 2019/11/6
 */
//@Component
public class LinBeanFactoryPostProcessor implements BeanFactoryPostProcessor
{
	@Override
	public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException
	{
		GenericBeanDefinition test = (GenericBeanDefinition) beanFactory.getBeanDefinition("test");
//		test.setBeanClass(LinService.class);
//		System.out.println("swap TestService to LinService");
	}
}
