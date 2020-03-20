/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.aop.framework;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.aopalliance.intercept.Interceptor;
import org.aopalliance.intercept.MethodInterceptor;

import org.springframework.aop.Advisor;
import org.springframework.aop.IntroductionAdvisor;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.adapter.AdvisorAdapterRegistry;
import org.springframework.aop.framework.adapter.GlobalAdvisorAdapterRegistry;
import org.springframework.aop.support.MethodMatchers;
import org.springframework.lang.Nullable;

/**
 * A simple but definitive way of working out an advice chain for a Method,
 * given an {@link Advised} object. Always rebuilds each advice chain;
 * caching can be provided by subclasses.
 *
 * @author Juergen Hoeller
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0.3
 */
@SuppressWarnings("serial")
public class DefaultAdvisorChainFactory implements AdvisorChainFactory, Serializable {

	@Override
	public List<Object> getInterceptorsAndDynamicInterceptionAdvice(
			Advised config, Method method, @Nullable Class<?> targetClass) {

		// This is somewhat tricky... We have to process introductions first,
		// but we need to preserve order in the ultimate list.
		// ����ֵ���ϣ�����װ�Ķ���Interceptor���������ӽӿ�MethodInterceptor
		List<Object> interceptorList = new ArrayList<>(config.getAdvisors().length);
		// ��ȡĿ���������
		Class<?> actualClass = (targetClass != null ? targetClass
				: method.getDeclaringClass());
		// �Ƿ�������
		boolean hasIntroductions = hasMatchingIntroductions(config, actualClass);
		// advisor������ע������
		// MethodBeforeAdviceAdapter����Advisor�����MethodBeforeAdvice
		// AfterReturningAdviceAdapter����Advisor�����AfterReturningAdvice
		// ThrowsAdviceAdapter����Advisor�����ThrowsAdvice
		AdvisorAdapterRegistry registry = GlobalAdvisorAdapterRegistry.getInstance();

		// ȥ�����������Ĺ����У���Ը�Ŀ�귽����ȡ�������к��ʵ�Advisor����
		for (Advisor advisor : config.getAdvisors()) {
			if (advisor instanceof PointcutAdvisor) {
				// Add it conditionally.
				PointcutAdvisor pointcutAdvisor = (PointcutAdvisor) advisor;
				// �����Advisor���Զ�Ŀ���������ǿ������к�������
				if (config.isPreFiltered()
						|| pointcutAdvisor.getPointcut().getClassFilter().matches(
								actualClass)) {
					// ��advisorת��MethodInterceptor
					MethodInterceptor[] interceptors = registry.getInterceptors(advisor);
					// ��ȡ����ƥ�������÷���ƥ�������Ը���ָ�����������ʽ���з���ƥ��
					MethodMatcher mm = pointcutAdvisor.getPointcut().getMethodMatcher();
					// ʹ�÷���ƥ������������з���ƥ��
					if (MethodMatchers.matches(mm, method, actualClass,
							hasIntroductions)) {
						// MethodMatcher�ӿ�ͨ�����ض���������matches()����
						// ����������matches()����Ϊ��̬ƥ�䣬��ƥ����������̫�ϸ�ʱʹ�ã���������󲿷ֳ�����ʹ��
						// ��֮Ϊ��̬����Ҫ������Ϊ����������matches()������Ҫ������ʱ��̬�ĶԲ��������ͽ���ƥ�䣻
						// ���������ķֽ��߾���boolean isRuntime()����
						// ����ƥ��ʱ��������������matches()��������ƥ�䣬��ƥ��ɹ�������boolean isRuntime()�ķ���ֵ
						// ��Ϊtrue�����������������matches()��������ƥ�䣨�����������Ķ�ƥ�䲻�У����������ıض�ƥ�䲻�У�

						// ��Ҫ���ݲ�����̬ƥ��
						if (mm.isRuntime()) {
							// Creating a new object instance in the getInterceptors() method
							// isn't a problem as we normally cache created chains.
							for (MethodInterceptor interceptor : interceptors) {
								interceptorList.add(new InterceptorAndDynamicMethodMatcher(interceptor, mm));
							}
						}
						else {
							interceptorList.addAll(Arrays.asList(interceptors));
						}
					}
				}
			}
			else if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (config.isPreFiltered() || ia.getClassFilter().matches(actualClass)) {
					Interceptor[] interceptors = registry.getInterceptors(advisor);
					interceptorList.addAll(Arrays.asList(interceptors));
				}
			}
			else {
				// ͨ��AdvisorAdapterRegistry��Advisor�������MethodInterceptor����
				Interceptor[] interceptors = registry.getInterceptors(advisor);
				interceptorList.addAll(Arrays.asList(interceptors));
			}
		}

		return interceptorList;
	}

	/**
	 * Determine whether the Advisors contain matching introductions.
	 */
	private static boolean hasMatchingIntroductions(Advised config, Class<?> actualClass) {
		for (Advisor advisor : config.getAdvisors()) {
			if (advisor instanceof IntroductionAdvisor) {
				IntroductionAdvisor ia = (IntroductionAdvisor) advisor;
				if (ia.getClassFilter().matches(actualClass)) {
					return true;
				}
			}
		}
		return false;
	}

}
