package com.micks.temp;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

/**
 * Create by micks on 2019/11/12
 */

//@Component
public class LinBeanPostProcessor implements BeanPostProcessor
{
	@Override
	public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException
	{
		System.out.println("==========" + beanName);
		return null;
	}
}
