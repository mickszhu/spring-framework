/*
 * Copyright 2002-2019 the original author or authors.
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

package org.springframework.web.servlet.mvc.method.annotation;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.MethodIntrospector;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.ReactiveAdapterRegistry;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.http.converter.ByteArrayHttpMessageConverter;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.support.AllEncompassingFormHttpMessageConverter;
import org.springframework.http.converter.xml.SourceHttpMessageConverter;
import org.springframework.lang.Nullable;
import org.springframework.ui.ModelMap;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ReflectionUtils.MethodFilter;
import org.springframework.web.accept.ContentNegotiationManager;
import org.springframework.web.bind.annotation.InitBinder;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.support.DefaultDataBinderFactory;
import org.springframework.web.bind.support.DefaultSessionAttributeStore;
import org.springframework.web.bind.support.SessionAttributeStore;
import org.springframework.web.bind.support.WebBindingInitializer;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.context.request.async.AsyncWebRequest;
import org.springframework.web.context.request.async.CallableProcessingInterceptor;
import org.springframework.web.context.request.async.DeferredResultProcessingInterceptor;
import org.springframework.web.context.request.async.WebAsyncManager;
import org.springframework.web.context.request.async.WebAsyncTask;
import org.springframework.web.context.request.async.WebAsyncUtils;
import org.springframework.web.method.ControllerAdviceBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.method.annotation.ErrorsMethodArgumentResolver;
import org.springframework.web.method.annotation.ExpressionValueMethodArgumentResolver;
import org.springframework.web.method.annotation.InitBinderDataBinderFactory;
import org.springframework.web.method.annotation.MapMethodProcessor;
import org.springframework.web.method.annotation.ModelAttributeMethodProcessor;
import org.springframework.web.method.annotation.ModelFactory;
import org.springframework.web.method.annotation.ModelMethodProcessor;
import org.springframework.web.method.annotation.RequestHeaderMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestHeaderMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMapMethodArgumentResolver;
import org.springframework.web.method.annotation.RequestParamMethodArgumentResolver;
import org.springframework.web.method.annotation.SessionAttributesHandler;
import org.springframework.web.method.annotation.SessionStatusMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.HandlerMethodArgumentResolverComposite;
import org.springframework.web.method.support.HandlerMethodReturnValueHandler;
import org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite;
import org.springframework.web.method.support.InvocableHandlerMethod;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.mvc.annotation.ModelAndViewResolver;
import org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.RequestContextUtils;
import org.springframework.web.util.WebUtils;

/**
 * Extension of {@link AbstractHandlerMethodAdapter} that supports
 * {@link RequestMapping} annotated {@code HandlerMethod}s.
 *
 * <p>Support for custom argument and return value types can be added via
 * {@link #setCustomArgumentResolvers} and {@link #setCustomReturnValueHandlers},
 * or alternatively, to re-configure all argument and return value types,
 * use {@link #setArgumentResolvers} and {@link #setReturnValueHandlers}.
 *
 * @author Rossen Stoyanchev
 * @author Juergen Hoeller
 * @author Sam Brannen
 * @since 3.1
 * @see HandlerMethodArgumentResolver
 * @see HandlerMethodReturnValueHandler
 */
public class RequestMappingHandlerAdapter extends AbstractHandlerMethodAdapter
		implements BeanFactoryAware, InitializingBean {

	/**
	 * MethodFilter that matches {@link InitBinder @InitBinder} methods.
	 */
	public static final MethodFilter INIT_BINDER_METHODS = method ->
			(AnnotationUtils.findAnnotation(method, InitBinder.class) != null);

	/**
	 * MethodFilter that matches {@link ModelAttribute @ModelAttribute} methods.
	 */
	public static final MethodFilter MODEL_ATTRIBUTE_METHODS = method ->
			(AnnotationUtils.findAnnotation(method, RequestMapping.class) == null &&
					AnnotationUtils.findAnnotation(method, ModelAttribute.class) != null);


	@Nullable
	private List<HandlerMethodArgumentResolver> customArgumentResolvers;

	@Nullable
	private HandlerMethodArgumentResolverComposite argumentResolvers;

	@Nullable
	private HandlerMethodArgumentResolverComposite initBinderArgumentResolvers;

	@Nullable
	private List<HandlerMethodReturnValueHandler> customReturnValueHandlers;

	@Nullable
	private HandlerMethodReturnValueHandlerComposite returnValueHandlers;

	@Nullable
	private List<ModelAndViewResolver> modelAndViewResolvers;

	private ContentNegotiationManager contentNegotiationManager = new ContentNegotiationManager();

	private List<HttpMessageConverter<?>> messageConverters;

	private List<Object> requestResponseBodyAdvice = new ArrayList<>();

	@Nullable
	private WebBindingInitializer webBindingInitializer;

	private AsyncTaskExecutor taskExecutor = new SimpleAsyncTaskExecutor("MvcAsync");

	@Nullable
	private Long asyncRequestTimeout;

	private CallableProcessingInterceptor[] callableInterceptors = new CallableProcessingInterceptor[0];

	private DeferredResultProcessingInterceptor[] deferredResultInterceptors = new DeferredResultProcessingInterceptor[0];

	private ReactiveAdapterRegistry reactiveAdapterRegistry = ReactiveAdapterRegistry.getSharedInstance();

	private boolean ignoreDefaultModelOnRedirect = false;

	private int cacheSecondsForSessionAttributeHandlers = 0;

	private boolean synchronizeOnSession = false;

	private SessionAttributeStore sessionAttributeStore = new DefaultSessionAttributeStore();

	private ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

	@Nullable
	private ConfigurableBeanFactory beanFactory;


	private final Map<Class<?>, SessionAttributesHandler> sessionAttributesHandlerCache = new ConcurrentHashMap<>(64);

	private final Map<Class<?>, Set<Method>> initBinderCache = new ConcurrentHashMap<>(64);

	private final Map<ControllerAdviceBean, Set<Method>> initBinderAdviceCache = new LinkedHashMap<>();

	private final Map<Class<?>, Set<Method>> modelAttributeCache = new ConcurrentHashMap<>(64);

	private final Map<ControllerAdviceBean, Set<Method>> modelAttributeAdviceCache = new LinkedHashMap<>();


	public RequestMappingHandlerAdapter() {
		StringHttpMessageConverter stringHttpMessageConverter = new StringHttpMessageConverter();
		stringHttpMessageConverter.setWriteAcceptCharset(false);  // see SPR-7316

		// 初始化消息转换器，为了处理http消息转换（比如使用MappingJackson处理JSON消息）
		this.messageConverters = new ArrayList<>(4);
		this.messageConverters.add(new ByteArrayHttpMessageConverter());
		this.messageConverters.add(stringHttpMessageConverter);
		this.messageConverters.add(new SourceHttpMessageConverter<>());
		this.messageConverters.add(new AllEncompassingFormHttpMessageConverter());
	}


	/**
	 * Provide resolvers for custom argument types. Custom resolvers are ordered
	 * after built-in ones. To override the built-in support for argument
	 * resolution use {@link #setArgumentResolvers} instead.
	 */
	public void setCustomArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		this.customArgumentResolvers = argumentResolvers;
	}

	/**
	 * Return the custom argument resolvers, or {@code null}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getCustomArgumentResolvers() {
		return this.customArgumentResolvers;
	}

	/**
	 * Configure the complete list of supported argument types thus overriding
	 * the resolvers that would otherwise be configured by default.
	 */
	public void setArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.argumentResolvers = null;
		}
		else {
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.argumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the configured argument resolvers, or possibly {@code null} if
	 * not initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getArgumentResolvers() {
		return (this.argumentResolvers != null ? this.argumentResolvers.getResolvers() : null);
	}

	/**
	 * Configure the supported argument types in {@code @InitBinder} methods.
	 */
	public void setInitBinderArgumentResolvers(@Nullable List<HandlerMethodArgumentResolver> argumentResolvers) {
		if (argumentResolvers == null) {
			this.initBinderArgumentResolvers = null;
		}
		else {
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite();
			this.initBinderArgumentResolvers.addResolvers(argumentResolvers);
		}
	}

	/**
	 * Return the argument resolvers for {@code @InitBinder} methods, or possibly
	 * {@code null} if not initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodArgumentResolver> getInitBinderArgumentResolvers() {
		return (this.initBinderArgumentResolvers != null ? this.initBinderArgumentResolvers.getResolvers() : null);
	}

	/**
	 * Provide handlers for custom return value types. Custom handlers are
	 * ordered after built-in ones. To override the built-in support for
	 * return value handling use {@link #setReturnValueHandlers}.
	 */
	public void setCustomReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		this.customReturnValueHandlers = returnValueHandlers;
	}

	/**
	 * Return the custom return value handlers, or {@code null}.
	 */
	@Nullable
	public List<HandlerMethodReturnValueHandler> getCustomReturnValueHandlers() {
		return this.customReturnValueHandlers;
	}

	/**
	 * Configure the complete list of supported return value types thus
	 * overriding handlers that would otherwise be configured by default.
	 */
	public void setReturnValueHandlers(@Nullable List<HandlerMethodReturnValueHandler> returnValueHandlers) {
		if (returnValueHandlers == null) {
			this.returnValueHandlers = null;
		}
		else {
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite();
			this.returnValueHandlers.addHandlers(returnValueHandlers);
		}
	}

	/**
	 * Return the configured handlers, or possibly {@code null} if not
	 * initialized yet via {@link #afterPropertiesSet()}.
	 */
	@Nullable
	public List<HandlerMethodReturnValueHandler> getReturnValueHandlers() {
		return (this.returnValueHandlers != null ? this.returnValueHandlers.getHandlers() : null);
	}

	/**
	 * Provide custom {@link ModelAndViewResolver}s.
	 * <p><strong>Note:</strong> This method is available for backwards
	 * compatibility only. However, it is recommended to re-write a
	 * {@code ModelAndViewResolver} as {@link HandlerMethodReturnValueHandler}.
	 * An adapter between the two interfaces is not possible since the
	 * {@link HandlerMethodReturnValueHandler#supportsReturnType} method
	 * cannot be implemented. Hence {@code ModelAndViewResolver}s are limited
	 * to always being invoked at the end after all other return value
	 * handlers have been given a chance.
	 * <p>A {@code HandlerMethodReturnValueHandler} provides better access to
	 * the return type and controller method information and can be ordered
	 * freely relative to other return value handlers.
	 */
	public void setModelAndViewResolvers(@Nullable List<ModelAndViewResolver> modelAndViewResolvers) {
		this.modelAndViewResolvers = modelAndViewResolvers;
	}

	/**
	 * Return the configured {@link ModelAndViewResolver}s, or {@code null}.
	 */
	@Nullable
	public List<ModelAndViewResolver> getModelAndViewResolvers() {
		return this.modelAndViewResolvers;
	}

	/**
	 * Set the {@link ContentNegotiationManager} to use to determine requested media types.
	 * If not set, the default constructor is used.
	 */
	public void setContentNegotiationManager(ContentNegotiationManager contentNegotiationManager) {
		this.contentNegotiationManager = contentNegotiationManager;
	}

	/**
	 * Provide the converters to use in argument resolvers and return value
	 * handlers that support reading and/or writing to the body of the
	 * request and response.
	 */
	public void setMessageConverters(List<HttpMessageConverter<?>> messageConverters) {
		this.messageConverters = messageConverters;
	}

	/**
	 * Return the configured message body converters.
	 */
	public List<HttpMessageConverter<?>> getMessageConverters() {
		return this.messageConverters;
	}

	/**
	 * Add one or more {@code RequestBodyAdvice} instances to intercept the
	 * request before it is read and converted for {@code @RequestBody} and
	 * {@code HttpEntity} method arguments.
	 */
	public void setRequestBodyAdvice(@Nullable List<RequestBodyAdvice> requestBodyAdvice) {
		if (requestBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(requestBodyAdvice);
		}
	}

	/**
	 * Add one or more {@code ResponseBodyAdvice} instances to intercept the
	 * response before {@code @ResponseBody} or {@code ResponseEntity} return
	 * values are written to the response body.
	 */
	public void setResponseBodyAdvice(@Nullable List<ResponseBodyAdvice<?>> responseBodyAdvice) {
		if (responseBodyAdvice != null) {
			this.requestResponseBodyAdvice.addAll(responseBodyAdvice);
		}
	}

	/**
	 * Provide a WebBindingInitializer with "global" initialization to apply
	 * to every DataBinder instance.
	 */
	public void setWebBindingInitializer(@Nullable WebBindingInitializer webBindingInitializer) {
		this.webBindingInitializer = webBindingInitializer;
	}

	/**
	 * Return the configured WebBindingInitializer, or {@code null} if none.
	 */
	@Nullable
	public WebBindingInitializer getWebBindingInitializer() {
		return this.webBindingInitializer;
	}

	/**
	 * Set the default {@link AsyncTaskExecutor} to use when a controller method
	 * return a {@link Callable}. Controller methods can override this default on
	 * a per-request basis by returning an {@link WebAsyncTask}.
	 * <p>By default a {@link SimpleAsyncTaskExecutor} instance is used.
	 * It's recommended to change that default in production as the simple executor
	 * does not re-use threads.
	 */
	public void setTaskExecutor(AsyncTaskExecutor taskExecutor) {
		this.taskExecutor = taskExecutor;
	}

	/**
	 * Specify the amount of time, in milliseconds, before concurrent handling
	 * should time out. In Servlet 3, the timeout begins after the main request
	 * processing thread has exited and ends when the request is dispatched again
	 * for further processing of the concurrently produced result.
	 * <p>If this value is not set, the default timeout of the underlying
	 * implementation is used, e.g. 10 seconds on Tomcat with Servlet 3.
	 * @param timeout the timeout value in milliseconds
	 */
	public void setAsyncRequestTimeout(long timeout) {
		this.asyncRequestTimeout = timeout;
	}

	/**
	 * Configure {@code CallableProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setCallableInterceptors(List<CallableProcessingInterceptor> interceptors) {
		this.callableInterceptors = interceptors.toArray(new CallableProcessingInterceptor[0]);
	}

	/**
	 * Configure {@code DeferredResultProcessingInterceptor}'s to register on async requests.
	 * @param interceptors the interceptors to register
	 */
	public void setDeferredResultInterceptors(List<DeferredResultProcessingInterceptor> interceptors) {
		this.deferredResultInterceptors = interceptors.toArray(new DeferredResultProcessingInterceptor[0]);
	}

	/**
	 * Configure the registry for reactive library types to be supported as
	 * return values from controller methods.
	 * @since 5.0
	 * @deprecated as of 5.0.5, in favor of {@link #setReactiveAdapterRegistry}
	 */
	@Deprecated
	public void setReactiveRegistry(ReactiveAdapterRegistry reactiveRegistry) {
		this.reactiveAdapterRegistry = reactiveRegistry;
	}

	/**
	 * Configure the registry for reactive library types to be supported as
	 * return values from controller methods.
	 * @since 5.0.5
	 */
	public void setReactiveAdapterRegistry(ReactiveAdapterRegistry reactiveAdapterRegistry) {
		this.reactiveAdapterRegistry = reactiveAdapterRegistry;
	}

	/**
	 * Return the configured reactive type registry of adapters.
	 * @since 5.0
	 */
	public ReactiveAdapterRegistry getReactiveAdapterRegistry() {
		return this.reactiveAdapterRegistry;
	}

	/**
	 * By default the content of the "default" model is used both during
	 * rendering and redirect scenarios. Alternatively a controller method
	 * can declare a {@link RedirectAttributes} argument and use it to provide
	 * attributes for a redirect.
	 * <p>Setting this flag to {@code true} guarantees the "default" model is
	 * never used in a redirect scenario even if a RedirectAttributes argument
	 * is not declared. Setting it to {@code false} means the "default" model
	 * may be used in a redirect if the controller method doesn't declare a
	 * RedirectAttributes argument.
	 * <p>The default setting is {@code false} but new applications should
	 * consider setting it to {@code true}.
	 * @see RedirectAttributes
	 */
	public void setIgnoreDefaultModelOnRedirect(boolean ignoreDefaultModelOnRedirect) {
		this.ignoreDefaultModelOnRedirect = ignoreDefaultModelOnRedirect;
	}

	/**
	 * Specify the strategy to store session attributes with. The default is
	 * {@link org.springframework.web.bind.support.DefaultSessionAttributeStore},
	 * storing session attributes in the HttpSession with the same attribute
	 * name as in the model.
	 */
	public void setSessionAttributeStore(SessionAttributeStore sessionAttributeStore) {
		this.sessionAttributeStore = sessionAttributeStore;
	}

	/**
	 * Cache content produced by {@code @SessionAttributes} annotated handlers
	 * for the given number of seconds.
	 * <p>Possible values are:
	 * <ul>
	 * <li>-1: no generation of cache-related headers</li>
	 * <li>0 (default value): "Cache-Control: no-store" will prevent caching</li>
	 * <li>1 or higher: "Cache-Control: max-age=seconds" will ask to cache content;
	 * not advised when dealing with session attributes</li>
	 * </ul>
	 * <p>In contrast to the "cacheSeconds" property which will apply to all general
	 * handlers (but not to {@code @SessionAttributes} annotated handlers),
	 * this setting will apply to {@code @SessionAttributes} handlers only.
	 * @see #setCacheSeconds
	 * @see org.springframework.web.bind.annotation.SessionAttributes
	 */
	public void setCacheSecondsForSessionAttributeHandlers(int cacheSecondsForSessionAttributeHandlers) {
		this.cacheSecondsForSessionAttributeHandlers = cacheSecondsForSessionAttributeHandlers;
	}

	/**
	 * Set if controller execution should be synchronized on the session,
	 * to serialize parallel invocations from the same client.
	 * <p>More specifically, the execution of the {@code handleRequestInternal}
	 * method will get synchronized if this flag is "true". The best available
	 * session mutex will be used for the synchronization; ideally, this will
	 * be a mutex exposed by HttpSessionMutexListener.
	 * <p>The session mutex is guaranteed to be the same object during
	 * the entire lifetime of the session, available under the key defined
	 * by the {@code SESSION_MUTEX_ATTRIBUTE} constant. It serves as a
	 * safe reference to synchronize on for locking on the current session.
	 * <p>In many cases, the HttpSession reference itself is a safe mutex
	 * as well, since it will always be the same object reference for the
	 * same active logical session. However, this is not guaranteed across
	 * different servlet containers; the only 100% safe way is a session mutex.
	 * @see org.springframework.web.util.HttpSessionMutexListener
	 * @see org.springframework.web.util.WebUtils#getSessionMutex(javax.servlet.http.HttpSession)
	 */
	public void setSynchronizeOnSession(boolean synchronizeOnSession) {
		this.synchronizeOnSession = synchronizeOnSession;
	}

	/**
	 * Set the ParameterNameDiscoverer to use for resolving method parameter names if needed
	 * (e.g. for default attribute names).
	 * <p>Default is a {@link org.springframework.core.DefaultParameterNameDiscoverer}.
	 */
	public void setParameterNameDiscoverer(ParameterNameDiscoverer parameterNameDiscoverer) {
		this.parameterNameDiscoverer = parameterNameDiscoverer;
	}

	/**
	 * A {@link ConfigurableBeanFactory} is expected for resolving expressions
	 * in method argument default values.
	 */
	@Override
	public void setBeanFactory(BeanFactory beanFactory) {
		if (beanFactory instanceof ConfigurableBeanFactory) {
			this.beanFactory = (ConfigurableBeanFactory) beanFactory;
		}
	}

	/**
	 * Return the owning factory of this bean instance, or {@code null} if none.
	 */
	@Nullable
	protected ConfigurableBeanFactory getBeanFactory() {
		return this.beanFactory;
	}


	@Override
	public void afterPropertiesSet() {
		// Do this first, it may add ResponseBody advice beans
		// 初始化Controller类通知缓存
		// 解析@ControllerAdvice类中的@InitBinder、@ModelAttribute注解
		initControllerAdviceCache();

		// 初始化参数解析器
		// 是判断Controller的方法参数是否可以利用已知的参数解析器进行解析（是否可以解析的boolean结果）
		// 这里面会去initBinderArgumentResolvers集合中的处理器Binder参数解析器
		if (this.argumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultArgumentResolvers();
			this.argumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
		// 初始化处理器Binder参数解析器
		// 是针对Controller的方法参数使用自定义PropertyEditor，进行参数的类型转换
		if (this.initBinderArgumentResolvers == null) {
			List<HandlerMethodArgumentResolver> resolvers = getDefaultInitBinderArgumentResolvers();
			this.initBinderArgumentResolvers = new HandlerMethodArgumentResolverComposite().addResolvers(resolvers);
		}
		// 初始化处理器返回值解析器
		// 处理不同类型的返回值的
		if (this.returnValueHandlers == null) {
			List<HandlerMethodReturnValueHandler> handlers = getDefaultReturnValueHandlers();
			this.returnValueHandlers = new HandlerMethodReturnValueHandlerComposite().addHandlers(handlers);
		}
	}

	private void initControllerAdviceCache() {
		if (getApplicationContext() == null) {
			return;
		}
		if (logger.isInfoEnabled()) {
			logger.info("Looking for @ControllerAdvice: " + getApplicationContext());
		}
		// 查找到所有被ControllerAdvice注解的类
		List<ControllerAdviceBean> adviceBeans = ControllerAdviceBean.findAnnotatedBeans(getApplicationContext());
		AnnotationAwareOrderComparator.sort(adviceBeans);

		List<Object> requestResponseBodyAdviceBeans = new ArrayList<>();

		for (ControllerAdviceBean adviceBean : adviceBeans) {
			Class<?> beanType = adviceBean.getBeanType();
			if (beanType == null) {
				throw new IllegalStateException("Unresolvable type for ControllerAdviceBean: " + adviceBean);
			}
			// 获取ControllerAdviceBean中带有ModelAttribute注解但是不带用RequestMapping注解的方法
			Set<Method> attrMethods = MethodIntrospector.selectMethods(beanType,
					MODEL_ATTRIBUTE_METHODS);
			if (!attrMethods.isEmpty()) {
				this.modelAttributeAdviceCache.put(adviceBean, attrMethods);
				if (logger.isInfoEnabled()) {
					logger.info("Detected @ModelAttribute methods in " + adviceBean);
				}
			}
			// 获取ControllerAdviceBean中带有InitBinder注解的方法
			Set<Method> binderMethods = MethodIntrospector.selectMethods(beanType,
					INIT_BINDER_METHODS);
			if (!binderMethods.isEmpty()) {
				this.initBinderAdviceCache.put(adviceBean, binderMethods);
				if (logger.isInfoEnabled()) {
					logger.info("Detected @InitBinder methods in " + adviceBean);
				}
			}
			// 如果该ControllerAdviceBean是RequestBodyAdvice的子类，则将该类放入集合中
			boolean isRequestBodyAdvice = RequestBodyAdvice.class.isAssignableFrom(beanType);
			// 如果该ControllerAdviceBean是ResponseBodyAdvice的子类，则将该类放入集合中
			boolean isResponseBodyAdvice = ResponseBodyAdvice.class.isAssignableFrom(beanType);
			if (isRequestBodyAdvice || isResponseBodyAdvice) {
				requestResponseBodyAdviceBeans.add(adviceBean);
				if (logger.isInfoEnabled()) {
					if (isRequestBodyAdvice) {
						logger.info("Detected RequestBodyAdvice bean in " + adviceBean);
					}
					else {
						logger.info("Detected ResponseBodyAdvice bean in " + adviceBean);
					}
				}
			}
		}

		if (!requestResponseBodyAdviceBeans.isEmpty()) {
			this.requestResponseBodyAdvice.addAll(0, requestResponseBodyAdviceBeans);
		}
	}

	/**
	 * Return the list of argument resolvers to use including built-in resolvers
	 * and custom resolvers provided via {@link #setCustomArgumentResolvers}.
	 */
	private List<HandlerMethodArgumentResolver> getDefaultArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

		// Annotation-based argument resolution
		// 基于注解的参数解析器
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ServletModelAttributeMethodProcessor(false));
		resolvers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RequestPartMethodArgumentResolver(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RequestHeaderMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new RequestHeaderMapMethodArgumentResolver());
		resolvers.add(new ServletCookieValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution
		// 基于类型的参数解析器
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());
		resolvers.add(new HttpEntityMethodProcessor(getMessageConverters(), this.requestResponseBodyAdvice));
		resolvers.add(new RedirectAttributesMethodArgumentResolver());
		resolvers.add(new ModelMethodProcessor());
		resolvers.add(new MapMethodProcessor());
		resolvers.add(new ErrorsMethodArgumentResolver());
		resolvers.add(new SessionStatusMethodArgumentResolver());
		resolvers.add(new UriComponentsBuilderMethodArgumentResolver());

		// Custom arguments
		// 自定义参数解析器
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));
		resolvers.add(new ServletModelAttributeMethodProcessor(true));

		return resolvers;
	}

	/**
	 * Return the list of argument resolvers to use for {@code @InitBinder}
	 * methods including built-in and custom resolvers.
	 */
	private List<HandlerMethodArgumentResolver> getDefaultInitBinderArgumentResolvers() {
		List<HandlerMethodArgumentResolver> resolvers = new ArrayList<>();

		// Annotation-based argument resolution
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), false));
		resolvers.add(new RequestParamMapMethodArgumentResolver());
		resolvers.add(new PathVariableMethodArgumentResolver());
		resolvers.add(new PathVariableMapMethodArgumentResolver());
		resolvers.add(new MatrixVariableMethodArgumentResolver());
		resolvers.add(new MatrixVariableMapMethodArgumentResolver());
		resolvers.add(new ExpressionValueMethodArgumentResolver(getBeanFactory()));
		resolvers.add(new SessionAttributeMethodArgumentResolver());
		resolvers.add(new RequestAttributeMethodArgumentResolver());

		// Type-based argument resolution
		resolvers.add(new ServletRequestMethodArgumentResolver());
		resolvers.add(new ServletResponseMethodArgumentResolver());

		// Custom arguments
		if (getCustomArgumentResolvers() != null) {
			resolvers.addAll(getCustomArgumentResolvers());
		}

		// Catch-all
		resolvers.add(new RequestParamMethodArgumentResolver(getBeanFactory(), true));

		return resolvers;
	}

	/**
	 * Return the list of return value handlers to use including built-in and
	 * custom handlers provided via {@link #setReturnValueHandlers}.
	 */
	private List<HandlerMethodReturnValueHandler> getDefaultReturnValueHandlers() {
		List<HandlerMethodReturnValueHandler> handlers = new ArrayList<>();

		// Single-purpose return value types
		handlers.add(new ModelAndViewMethodReturnValueHandler());
		handlers.add(new ModelMethodProcessor());
		handlers.add(new ViewMethodReturnValueHandler());
		handlers.add(new ResponseBodyEmitterReturnValueHandler(getMessageConverters(),
				this.reactiveAdapterRegistry, this.taskExecutor, this.contentNegotiationManager));
		handlers.add(new StreamingResponseBodyReturnValueHandler());
		handlers.add(new HttpEntityMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));
		handlers.add(new HttpHeadersReturnValueHandler());
		handlers.add(new CallableMethodReturnValueHandler());
		handlers.add(new DeferredResultMethodReturnValueHandler());
		handlers.add(new AsyncTaskMethodReturnValueHandler(this.beanFactory));

		// Annotation-based return value types
		handlers.add(new ModelAttributeMethodProcessor(false));
		handlers.add(new RequestResponseBodyMethodProcessor(getMessageConverters(),
				this.contentNegotiationManager, this.requestResponseBodyAdvice));

		// Multi-purpose return value types
		handlers.add(new ViewNameMethodReturnValueHandler());
		handlers.add(new MapMethodProcessor());

		// Custom return value types
		if (getCustomReturnValueHandlers() != null) {
			handlers.addAll(getCustomReturnValueHandlers());
		}

		// Catch-all
		if (!CollectionUtils.isEmpty(getModelAndViewResolvers())) {
			handlers.add(new ModelAndViewResolverMethodReturnValueHandler(getModelAndViewResolvers()));
		}
		else {
			handlers.add(new ModelAttributeMethodProcessor(true));
		}

		return handlers;
	}


	/**
	 * Always return {@code true} since any method argument and return value
	 * type will be processed in some way. A method argument not recognized
	 * by any HandlerMethodArgumentResolver is interpreted as a request parameter
	 * if it is a simple type, or as a model attribute otherwise. A return value
	 * not recognized by any HandlerMethodReturnValueHandler will be interpreted
	 * as a model attribute.
	 */
	@Override
	protected boolean supportsInternal(HandlerMethod handlerMethod) {
		return true;
	}

	@Override
	protected ModelAndView handleInternal(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

		ModelAndView mav;
		checkRequest(request);

		// Execute invokeHandlerMethod in synchronized block if required.
		if (this.synchronizeOnSession) {
			HttpSession session = request.getSession(false);
			if (session != null) {
				Object mutex = WebUtils.getSessionMutex(session);
				synchronized (mutex) {
					mav = invokeHandlerMethod(request, response, handlerMethod);
				}
			}
			else {
				// No HttpSession available -> no mutex necessary
				mav = invokeHandlerMethod(request, response, handlerMethod);
			}
		}
		else {
			// No synchronization on session demanded at all...
			// 调用HandlerMethod对象，其实也就是调用Controller中的方法
			// handlerMethod对象就是处理器对象
			mav = invokeHandlerMethod(request, response, handlerMethod);
		}

		if (!response.containsHeader(HEADER_CACHE_CONTROL)) {
			if (getSessionAttributesHandler(handlerMethod).hasSessionAttributes()) {
				applyCacheSeconds(response, this.cacheSecondsForSessionAttributeHandlers);
			}
			else {
				prepareResponse(response);
			}
		}

		return mav;
	}

	/**
	 * This implementation always returns -1. An {@code @RequestMapping} method can
	 * calculate the lastModified value, call {@link WebRequest#checkNotModified(long)},
	 * and return {@code null} if the result of that call is {@code true}.
	 */
	@Override
	protected long getLastModifiedInternal(HttpServletRequest request, HandlerMethod handlerMethod) {
		return -1;
	}


	/**
	 * Return the {@link SessionAttributesHandler} instance for the given handler type
	 * (never {@code null}).
	 */
	private SessionAttributesHandler getSessionAttributesHandler(HandlerMethod handlerMethod) {
		Class<?> handlerType = handlerMethod.getBeanType();
		SessionAttributesHandler sessionAttrHandler = this.sessionAttributesHandlerCache.get(handlerType);
		if (sessionAttrHandler == null) {
			synchronized (this.sessionAttributesHandlerCache) {
				sessionAttrHandler = this.sessionAttributesHandlerCache.get(handlerType);
				if (sessionAttrHandler == null) {
					sessionAttrHandler = new SessionAttributesHandler(handlerType, sessionAttributeStore);
					this.sessionAttributesHandlerCache.put(handlerType, sessionAttrHandler);
				}
			}
		}
		return sessionAttrHandler;
	}

	/**
	 * Invoke the {@link RequestMapping} handler method preparing a {@link ModelAndView}
	 * if view resolution is required.
	 * @since 4.2
	 * @see #createInvocableHandlerMethod(HandlerMethod)
	 */
	@Nullable
	protected ModelAndView invokeHandlerMethod(HttpServletRequest request,
			HttpServletResponse response, HandlerMethod handlerMethod) throws Exception {

		ServletWebRequest webRequest = new ServletWebRequest(request, response);
		try {
			// 获取当前HandlerMethod所对应的Controller中配置的InitBinder
			// 用于进行参数的绑定
			// 获取数据绑定工厂，作用是为了获取WebDataBinder,WebDataBinder给参数进行类型转换
			// 主要是针对@InitBinder注解进行处理
			WebDataBinderFactory binderFactory = getDataBinderFactory(handlerMethod);
			// 获取容器中全局配置的ModelAttribute
			// 获取当前当前HandlerMethod所对应的Controller中配置的ModelAttribute
			// 这些配置的方法将会在目标方法调用之前进行调用
			// 主要是针对@ModelAttribute注解进行处理
			ModelFactory modelFactory = getModelFactory(handlerMethod, binderFactory);

			// 将handlerMethod封装为一个ServletInvocableHandlerMethod对象，
			// 该对象用于对当前request的整体调用流程进行了封装
			ServletInvocableHandlerMethod invocableMethod = createInvocableHandlerMethod(
					handlerMethod);
			// 设置参数解析器
			if (this.argumentResolvers != null) {
				// 设置当前容器中配置的所有ArgumentResolver
				invocableMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
			}
			// 设置返回值解析器
			if (this.returnValueHandlers != null) {
				// 设置当前容器中配置的所有ReturnValueHandler
				invocableMethod.setHandlerMethodReturnValueHandlers(this.returnValueHandlers);
			}
			invocableMethod.setDataBinderFactory(binderFactory);
			// parameterNameDiscoverer : 参数名称发现者。通过反射可以很容易获取参数的类型，但是参数名称一般都和源码的不一样
			invocableMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);

			ModelAndViewContainer mavContainer = new ModelAndViewContainer();
			mavContainer.addAllAttributes(RequestContextUtils.getInputFlashMap(request));
			// 这里initModel()方法主要作用是调用前面获取到的@ModelAttribute标注的方法，
			// 从而达到@ModelAttribute标注的方法能够在目标Handler调用之前调用的目的
			modelFactory.initModel(webRequest, mavContainer, invocableMethod);
			mavContainer.setIgnoreDefaultModelOnRedirect(this.ignoreDefaultModelOnRedirect);

			AsyncWebRequest asyncWebRequest = WebAsyncUtils.createAsyncWebRequest(request, response);
			asyncWebRequest.setTimeout(this.asyncRequestTimeout);

			// 封装异步任务的线程池，request和interceptors到WebAsyncManager中
			WebAsyncManager asyncManager = WebAsyncUtils.getAsyncManager(request);
			asyncManager.setTaskExecutor(this.taskExecutor);
			asyncManager.setAsyncWebRequest(asyncWebRequest);
			asyncManager.registerCallableInterceptors(this.callableInterceptors);
			asyncManager.registerDeferredResultInterceptors(this.deferredResultInterceptors);

			// 这里就是用于判断当前请求是否有异步任务结果的，如果存在，则对异步任务结果进行封装
			if (asyncManager.hasConcurrentResult()) {
				Object result = asyncManager.getConcurrentResult();
				mavContainer = (ModelAndViewContainer) asyncManager.getConcurrentResultContext()[0];
				asyncManager.clearConcurrentResult();
				if (logger.isDebugEnabled()) {
					logger.debug("Found concurrent result value [" + result + "]");
				}
				invocableMethod = invocableMethod.wrapConcurrentResult(result);
			}

			// 调用HandlerMethod
			// 对请求参数进行处理，调用目标HandlerMethod，并且将返回值封装为一个ModelAndView对象
			invocableMethod.invokeAndHandle(webRequest, mavContainer);
			if (asyncManager.isConcurrentHandlingStarted()) {
				return null;
			}
			// 对封装的ModelAndView进行处理，主要是判断当前请求是否进行了重定向，如果进行了重定向，
			// 还会判断是否需要将FlashAttributes封装到新的请求中
			return getModelAndView(mavContainer, modelFactory, webRequest);
		}
		finally {
			webRequest.requestCompleted();
		}
	}

	/**
	 * Create a {@link ServletInvocableHandlerMethod} from the given {@link HandlerMethod} definition.
	 * @param handlerMethod the {@link HandlerMethod} definition
	 * @return the corresponding {@link ServletInvocableHandlerMethod} (or custom subclass thereof)
	 * @since 4.2
	 */
	protected ServletInvocableHandlerMethod createInvocableHandlerMethod(HandlerMethod handlerMethod) {
		return new ServletInvocableHandlerMethod(handlerMethod);
	}

	private ModelFactory getModelFactory(HandlerMethod handlerMethod, WebDataBinderFactory binderFactory) {
		// @SessionAttributes注解解析
		// 这里SessionAttributeHandler的作用是声明几个属性，使其能够在多个请求之间共享，
	    // 并且其能够保证当前request返回的model中始终保有这些属性
		SessionAttributesHandler sessionAttrHandler = getSessionAttributesHandler(handlerMethod);
		// 判断缓存中是否保存有当前handler执行之前所需要执行的标注了@ModelAttribute的方法
		Class<?> handlerType = handlerMethod.getBeanType();
		Set<Method> methods = this.modelAttributeCache.get(handlerType);
		if (methods == null) {
			// 如果缓存中没有相关属性，那么就在当前bean中查找所有使用@ModelAttribute标注，但是
	        // 没有使用@RequestMapping标注的方法，并将这些方法缓存起来
			methods = MethodIntrospector.selectMethods(handlerType,
					MODEL_ATTRIBUTE_METHODS);
			this.modelAttributeCache.put(handlerType, methods);
		}
		List<InvocableHandlerMethod> attrMethods = new ArrayList<>();
		// Global methods first
		// 获取全局的使用@ModelAttribute标注，但是没有使用@RequestMapping标注的方法，
	    // 这里全局类型的方法的声明方式需要注意的是，其所在的bean必须使用@ControllerAdvice进行标注
		this.modelAttributeAdviceCache.forEach((clazz, methodSet) -> {
			if (clazz.isApplicableToBeanType(handlerType)) {
				Object bean = clazz.resolveBean();
				for (Method method : methodSet) {
					attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
				}
			}
		});
		
		// 将当前方法中使用@ModelAttribute标注的方法添加到需要执行的attrMethods中。从这里的添加顺序
	    // 可以看出，全局类型的方法将会先于局部类型的方法执行
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			attrMethods.add(createModelAttributeMethod(binderFactory, bean, method));
		}
		return new ModelFactory(attrMethods, binderFactory, sessionAttrHandler);
	}
	
	/**
	 * 这里获取InitBinder的方式主要有两种，
	 * 一种是获取全局配置的InitBinder，全局类型的InitBinder需要声明的类上使用@ControllerAdvice进行标注，并且声明方法上使用@InitBinder进行标注；
	 * 另一种则是获取当前handler所在类中的使用@InitBinder注解标注的方法。这两种InitBinder都会执行，只不过全局类型的InitBinder会先于局部类型的InitBinder执行。
	 * 关于使用@InitBinder标注的方法的执行时间点，需要说明的是，因为其与参数绑定有关，因而其只会在参数绑定时才会执行。
	 * @param factory
	 * @param bean
	 * @param method
	 * @return
	 */
	private InvocableHandlerMethod createModelAttributeMethod(WebDataBinderFactory factory, Object bean, Method method) {
		InvocableHandlerMethod attrMethod = new InvocableHandlerMethod(bean, method);
		if (this.argumentResolvers != null) {
			attrMethod.setHandlerMethodArgumentResolvers(this.argumentResolvers);
		}
		attrMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		attrMethod.setDataBinderFactory(factory);
		return attrMethod;
	}

	private WebDataBinderFactory getDataBinderFactory(HandlerMethod handlerMethod) throws Exception {
		// 获取处理器类型
		// 判断当前缓存中是否缓存了当前bean所需要装配的InitBinder方法，如果存在，则直接从缓存中取，
		// 如果不存在，则在当前bean中进行扫描获取
		Class<?> handlerType = handlerMethod.getBeanType();
		// 获取指定Handler类中使用@InitBinder注解的方法
		Set<Method> methods = this.initBinderCache.get(handlerType);
		if (methods == null) {
			// 在当前bean中查找所有标注了@InitBinder注解的方法，这里INIT_BINDER_METHODS就是一个
			// 选择器，表示只获取使用@InitBinder标注的方法
			methods = MethodIntrospector.selectMethods(handlerType, INIT_BINDER_METHODS);
			this.initBinderCache.put(handlerType, methods);
		}
		// 这里initBinderAdviceCache是在RequestMappingHandlerAdapter初始化时同步初始化的，
		// 其内包含的方法有如下两个特点：
		// ①当前方法所在类使用@ControllerAdvice进行标注了；
		// ②当前方法使用@InitBinder进行了标注。也就是说其内保存的方法可以理解为是全局类型的参数绑定方法
		List<InvocableHandlerMethod> initBinderMethods = new ArrayList<>();
		// Global methods first
		// 获取@ControllerAdvice类中使用@InitBinder注解的方法，并添加到DataBinderFactory初始化参数中
		this.initBinderAdviceCache.forEach((clazz, methodSet) -> {
			// 这里判断的是当前配置的全局类型的InitBinder是否能够应用于当前bean，
			// 判断的方式主要在@ControllerAdvice注解中进行了声明，包括通过包名，类所在的包，
			// 接口或者注解的形式限定的范围
			if (clazz.isApplicableToBeanType(handlerType)) {
				Object bean = clazz.resolveBean();
				for (Method method : methodSet) {
					initBinderMethods.add(createInitBinderMethod(bean, method));
				}
			}
		});
		// 将获取到的指定Handler类中使用@InitBinder注解的方法，添加到DataBinderFactory初始化参数中
		// 这里是将当前HandlerMethod所在bean中的InitBinder添加到需要执行的initBinderMethods中。
		// 这里从添加的顺序可以看出，全局类型的InitBinder会在当前bean中的InitBinder之前执行
		for (Method method : methods) {
			Object bean = handlerMethod.getBean();
			initBinderMethods.add(createInitBinderMethod(bean, method));
		}
		// 将需要执行的InitBinder封装到InitBinderDataBinderFactory中
		return createDataBinderFactory(initBinderMethods);
	}

	private InvocableHandlerMethod createInitBinderMethod(Object bean, Method method) {
		InvocableHandlerMethod binderMethod = new InvocableHandlerMethod(bean, method);
		if (this.initBinderArgumentResolvers != null) {
			binderMethod.setHandlerMethodArgumentResolvers(this.initBinderArgumentResolvers);
		}
		binderMethod.setDataBinderFactory(new DefaultDataBinderFactory(this.webBindingInitializer));
		binderMethod.setParameterNameDiscoverer(this.parameterNameDiscoverer);
		return binderMethod;
	}

	/**
	 * Template method to create a new InitBinderDataBinderFactory instance.
	 * <p>The default implementation creates a ServletRequestDataBinderFactory.
	 * This can be overridden for custom ServletRequestDataBinder subclasses.
	 * @param binderMethods {@code @InitBinder} methods
	 * @return the InitBinderDataBinderFactory instance to use
	 * @throws Exception in case of invalid state or arguments
	 */
	protected InitBinderDataBinderFactory createDataBinderFactory(List<InvocableHandlerMethod> binderMethods)
			throws Exception {

		return new ServletRequestDataBinderFactory(binderMethods, getWebBindingInitializer());
	}

	@Nullable
	private ModelAndView getModelAndView(ModelAndViewContainer mavContainer,
			ModelFactory modelFactory, NativeWebRequest webRequest) throws Exception {

		modelFactory.updateModel(webRequest, mavContainer);
		if (mavContainer.isRequestHandled()) {
			return null;
		}
		ModelMap model = mavContainer.getModel();
		ModelAndView mav = new ModelAndView(mavContainer.getViewName(), model, mavContainer.getStatus());
		if (!mavContainer.isViewReference()) {
			mav.setView((View) mavContainer.getView());
		}
		if (model instanceof RedirectAttributes) {
			Map<String, ?> flashAttributes = ((RedirectAttributes) model).getFlashAttributes();
			HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
			if (request != null) {
				RequestContextUtils.getOutputFlashMap(request).putAll(flashAttributes);
			}
		}
		return mav;
	}

}
