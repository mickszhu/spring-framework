package com.lin.service;

import org.springframework.stereotype.Service;

/**
 * Create by ljc on 2019/11/6
 */

@Service("testService")
public class TestService
{
	public TestService()
	{
		System.out.println("TestService 无参构造函数");
	}
}
