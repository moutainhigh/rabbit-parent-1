package cn.xuyk.rabbit.producer.broker.container;

import cn.xuyk.rabbit.api.common.MessageType;
import cn.xuyk.rabbit.api.exception.MessageRunTimeException;
import cn.xuyk.rabbit.api.pojo.Message;
import cn.xuyk.rabbit.common.convert.GenericMessageConverter;
import cn.xuyk.rabbit.common.convert.RabbitMessageConverter;
import cn.xuyk.rabbit.common.serializer.Serializer;
import cn.xuyk.rabbit.common.serializer.SerializerFactory;
import cn.xuyk.rabbit.common.serializer.impl.JacksonSerializerFactory;
import cn.xuyk.rabbit.producer.broker.confirm.ConfirmCallback;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * @Author: Xuyk
 * @Description:  池化封装/缓存
 *  * 	每一个topic 对应一个RabbitTemplate
 *  *	1.	提高发送消息的效率
 *  * 	2. 	可以根据不同的需求制定化不同的RabbitTemplate, 比如每一个topic 都有自己的routingKey规则
 * @Date: 2020/6/27
 */
@Slf4j
@Component
public class RabbitTemplateContainer {
    /**
     * 缓存 map<topic,template>
     */
	private Map<String, RabbitTemplate> rabbitMap = Maps.newConcurrentMap();

	private SerializerFactory serializerFactory = JacksonSerializerFactory.INSTANCE;

	@Autowired
	private ConnectionFactory connectionFactory;
	@Autowired
	private ConfirmCallback confirmCallback;
	
    /**
     * 根据不同消息获取相应的template
     * @param message
     * @return
     * @throws MessageRunTimeException
     */
	public RabbitTemplate getTemplate(Message message) throws MessageRunTimeException {
		Preconditions.checkNotNull(message);
		String topic = message.getTopic();
		RabbitTemplate rabbitTemplate = rabbitMap.get(topic);
		// 1.如果template池中存在则直接取出返回
		if(rabbitTemplate != null) {
			return rabbitTemplate;
		}
		log.info("#RabbitTemplateContainer.getTemplate# topic: {} is not exists, create one", topic);

		// 2.池中不存在对应的template则新增
		RabbitTemplate newTemplate = new RabbitTemplate(connectionFactory);
		newTemplate.setExchange(topic);
		newTemplate.setRoutingKey(message.getRoutingKey());
		newTemplate.setRetryTemplate(new RetryTemplate());
		
		// 3.添加序列化反序列化和converter对象
		Serializer serializer = serializerFactory.create();
		GenericMessageConverter gmc = new GenericMessageConverter(serializer);
		// 装饰者模式
		RabbitMessageConverter rmc = new RabbitMessageConverter(gmc);
		newTemplate.setMessageConverter(rmc);

		// 4.如果消息类型不是迅速消息，都需要设置确认回调
		String messageType = message.getMessageType();
		if(!MessageType.RAPID.equals(messageType)) {
			newTemplate.setConfirmCallback(confirmCallback);
		}
		
		rabbitMap.putIfAbsent(topic, newTemplate);
		
		return rabbitMap.get(topic);
	}

}
