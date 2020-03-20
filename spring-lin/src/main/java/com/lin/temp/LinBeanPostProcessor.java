package com.lin.temp;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

/**
 * Create by ljc on 2019/11/12
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
