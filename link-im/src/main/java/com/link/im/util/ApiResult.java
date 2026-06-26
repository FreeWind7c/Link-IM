package com.link.im.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.link.im.enums.BaseEnum;

import java.util.HashMap;
import java.util.Map;

/**
 * 返回数据
 *
 * @author Mark sunlightcs@gmail.com
 */
public class ApiResult extends HashMap<String, Object> {
	private static final long serialVersionUID = 1L;



	public static ApiResult isPay() {
		ApiResult apiResult = new ApiResult();
		apiResult.put("code",200);
		apiResult.put("status",1);
		apiResult.put("msg","已支付");
		return apiResult;
	}

	public static ApiResult notPay(String msg){
		ApiResult apiResult = new ApiResult();
		apiResult.put("code",200);
		apiResult.put("status",0);
		apiResult.put("msg",msg);
		return apiResult;
	}

	public static ApiResult notLogin() {
		ApiResult apiResult = new ApiResult();
		apiResult.put("code",500);
		apiResult.put("msg","请先登录");
		return apiResult;
	}



    public ApiResult setData(Object data) {
		put("data",data);
		return this;
	}

	//利用fastjson进行反序列化
	public <T> T getData(TypeReference<T> typeReference) {
		Object data = get("data");	//默认是map
		String jsonString = JSON.toJSONString(data);
		T t = JSON.parseObject(jsonString, typeReference);
		return t;
	}


	//利用fastjson进行反序列化
	public <T> T getData(String key,TypeReference<T> typeReference) {
		Object data = get(key);	//默认是map
		String jsonString = JSON.toJSONString(data);
		T t = JSON.parseObject(jsonString, typeReference);
		return t;
	}



	public ApiResult() {
		put("code", 200);
		put("msg", "success");
	}
	
	public static ApiResult error() {
		return error(500, "ERROR");
	}


	
	public static ApiResult error(String msg) {
		return error(500, msg);

	}

	public static ApiResult error(BaseEnum base) {
		return error(base.getCode(), base.getMessage());
	}

	
	public static ApiResult error(int code, String msg) {
		ApiResult apiResult = new ApiResult();
		apiResult.put("code", code);
		apiResult.put("msg", msg);
		return apiResult;
	}

	public static ApiResult success(String msg) {
		ApiResult apiResult = new ApiResult();
		apiResult.put("code",200);
		apiResult.put("msg", msg);
		return apiResult;
	}
	

	
	public static ApiResult success() {
		ApiResult apiResult = new ApiResult();
		apiResult.put("code",200);
		apiResult.put("msg","SUCCESS");
		return apiResult;
	}

	public static ApiResult success(BaseEnum base) {
		ApiResult apiResult = new ApiResult();
		apiResult.put("code",base.getCode());
		apiResult.put("msg",base.getMessage());
		return apiResult;
	}

	public static ApiResult record() {
		ApiResult apiResult = new ApiResult();
		apiResult.put("code",1);
		apiResult.put("msg","SUCCESS");
		return apiResult;
	}

	public static ApiResult collected(){
		ApiResult apiResult = new ApiResult();
		apiResult.put("code",0);
		apiResult.put("msg","ERROR");
		return apiResult;
	}


	public static ApiResult notBuy() {
		ApiResult apiResult = new ApiResult();
		apiResult.put("code",200);
		apiResult.put("status",0);
		apiResult.put("msg","未购买");
		return apiResult;
	}

	public static ApiResult isBuy() {
		ApiResult apiResult = new ApiResult();
		apiResult.put("code",200);
		apiResult.put("status",1);
		apiResult.put("msg","已购买");
		return apiResult;
	}

	public ApiResult put(Object value) {
		super.put("msg", value);
		return this;
	}

	public ApiResult putData(String key, Object value){
		ApiResult apiResult = new ApiResult();
		apiResult.put(key,value);
		return apiResult;
	}

	public Integer getCode() {

		return (Integer) this.get("code");
	}




	public ApiResult setToken(String token) {
		this.put("token",token);
		return this;
	}

	public ApiResult putMsg(String msg, String appHttpCodeEnum) {
		put("msg",appHttpCodeEnum);
		return this;
	}

	public ApiResult setAuth(Integer auth) {
		ApiResult apiResult = new ApiResult();
		apiResult.put("auth",auth);
		return apiResult;
	}

	public ApiResult setTokenAndAuth(String token, Integer auth) {
		ApiResult apiResult = new ApiResult();
		apiResult.put("token",token);
		apiResult.put("auth",auth);
		return apiResult;
	}

	public ApiResult put0(String key, Object value) {
		this.put(key,value);
		return this;

	}

	public ApiResult setTask(Runnable task) {
		ApiResult apiResult = new ApiResult();
		apiResult.put("task",1);
		task.run();;
		return apiResult;
	}

	public ApiResult setSize(int size) {
		this.put("size",size);
		return this;
	}

	public ApiResult setUrl(String url) {
		this.put("url",url);
		return this;
	}

	public ApiResult setChildrenData(Object data) {
		this.put("children",data);
		return this;
	}
}