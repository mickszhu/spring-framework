# <img src="src/docs/asciidoc/images/spring-framework.png" width="80" height="80"> Spring Framework

## 概述
1. 包含Spring-framework源码，可以自由修改。
2. 作为自己阅读Spring-framework源码使用，会添加自己的一些注释信息。

## 版本
5.0.x

## 使用说明
如果需要，你可以fork本项目，按照如下步骤配置后，作为自己阅读源码使用：
1. 使用git clone下载本项目。
2. 使用IDEA，File-->Open来打开该项目。
3. 使用Gradle4.4.1构建，理论上4.1版本以上都可以。
4. Module为spring-lin是我自己创建用于测试整个项目，你可以在这个Module下自行增加自己的需求，同时该Module也作为阅读的入口。

## 注意事项
1. 如果spring-lin这个Module报错，你可以删掉这个Module，重新创建自己的Module。
2. 在第一次加载项目时候，gradle会下载一些包，所以我在gradle.properties文件中配置了网络代理，如果你不需要或者配置与我不同可以自行修改。

