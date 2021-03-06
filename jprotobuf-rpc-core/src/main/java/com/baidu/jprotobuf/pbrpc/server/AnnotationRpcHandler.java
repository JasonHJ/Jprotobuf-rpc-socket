/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.baidu.jprotobuf.pbrpc.server;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.logging.Logger;

import org.springframework.util.SerializationUtils;

import com.baidu.bjf.remoting.protobuf.Codec;
import com.baidu.bjf.remoting.protobuf.ProtobufProxy;
import com.baidu.jprotobuf.pbrpc.ProtobufRPCService;
import com.baidu.jprotobuf.pbrpc.intercept.MethodInvocationInfo;
import com.baidu.jprotobuf.pbrpc.management.ServerStatus;
import com.baidu.jprotobuf.pbrpc.utils.ServiceSignatureUtils;

/**
 * RPC handler for Jprotobuf annotation.
 * 
 * @author xiemalin
 * @since 1.0
 */
@SuppressWarnings({ "unchecked", "rawtypes" })
public class AnnotationRpcHandler extends AbstractAnnotationRpcHandler {
	/**
	 * Logger for this class
	 */
	private static final Logger PERFORMANCE_LOGGER = Logger.getLogger("performance-log");

	private Codec inputCodec;
	private Codec outputCodec;

	private String serviceSignature;


	/**
	 * @param method
	 * @param service
	 */
	public AnnotationRpcHandler(Method method, Object service, ProtobufRPCService protobufPRCService) {
		super(method, service, protobufPRCService);
		if (getInputClass() != null) {
			inputCodec = ProtobufProxy.create(getInputClass());
		}
		if (getOutputClass() != null) {
			outputCodec = ProtobufProxy.create(getOutputClass());
		}

		serviceSignature = ServiceSignatureUtils.makeSignature(getServiceName(), getMethodName());

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.baidu.jprotobuf.pbrpc.RpcHandler#doRealHandle(byte[])
	 */
	protected RpcData doRealHandle(RpcData data) throws Exception {
		Object input = null;
		Object[] param;
		Object ret = null;
		if (data.getData() != null && inputCodec != null) {
			input = inputCodec.decode(data.getData());
			param = new Object[] { input };
		} else {
			param = new Object[0];
		}

		RpcData retData = new RpcData();
		// process attachment
		if (getAttachmentHandler() != null) {
			byte[] responseAttachment = getAttachmentHandler().handleAttachement(data.getAttachment(), getServiceName(),
					getMethodName(), param);
			retData.setAttachment(responseAttachment);
		}

		long time = System.currentTimeMillis();

		// check intercepter
		if (getInterceptor() != null) {
			MethodInvocationInfo methodInvocationInfo = new MethodInvocationInfo(getService(), param, getMethod(), data.getExtraParams());
			getInterceptor().beforeInvoke(methodInvocationInfo);
			
			ret = getInterceptor().process(methodInvocationInfo);
			if (ret != null) {
				PERFORMANCE_LOGGER.fine("RPC client invoke method(by intercepter) '" + getMethod().getName()
						+ "' time took:" + (System.currentTimeMillis() - time) + " ms");

				if (outputCodec != null) {
					byte[] response = outputCodec.encode(ret);
					retData.setData(response);
				}

				return retData;
			}
		}

		ret = getMethod().invoke(getService(), param);
		long took = (System.currentTimeMillis() - time);
		PERFORMANCE_LOGGER
				.fine("RPC server invoke method(local) '" + getMethod().getName() + "' time took:" + took + " ms");

		ServerStatus.incr(serviceSignature, took);

		if (ret == null) {
			return retData;
		}

		if (outputCodec != null) {
			byte[] response = outputCodec.encode(ret);
			retData.setData(response);
		}

		return retData;
	}

}
