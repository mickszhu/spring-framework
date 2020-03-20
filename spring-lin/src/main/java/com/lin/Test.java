package com.lin;

import com.lin.service.TestService;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Create by ljc on 2019/11/6
 */

public class Test
{
	public static void main(String[] args)
	{
		/**
		 * 使用注释启动Spring
		 * 把类扫描出来
		 * 把bean实例化
		 */
		AnnotationConfigApplicationContext context =
				new AnnotationConfigApplicationContext(AppConfig.class);


		/**
		 * 使用xml配置文件启动Spring
		 */
//		ApplicationContext context = new ClassPathXmlApplicationContext("spring-config.xml");


		//获取对应类
		System.out.println(context.containsBean("testService"));
		TestService testService = (TestService) context.getBean("testService");
		System.out.println(testService);

	}
}