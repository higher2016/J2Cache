package net.oschina.j2cache.redis;

import net.oschina.j2cache.ClusterPolicy;
import net.oschina.j2cache.Command;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import redis.clients.jedis.BinaryJedisPubSub;
import redis.clients.jedis.JedisCluster;

/**
 * 使用 Redis 的订阅和发布进行集群中的节点通知
 * @author winterlau
 */
public class RedisPubSubClusterPolicy extends BinaryJedisPubSub implements ClusterPolicy {

    private final static Log log = LogFactory.getLog(RedisPubSubClusterPolicy.class);

    private JedisCluster redis;
    private String channel;

    public RedisPubSubClusterPolicy(String channel, JedisCluster redis){
        this.redis = redis;
        this.channel = channel;
    }

    /**
     * 使用 Redis 的发布订阅通道
     */
    @Override
    public void connect() {
        redis.subscribe(this, channel.getBytes());
    }

    /**
     * 发送清除缓存的广播命令
     *
     * @param region : Cache region name
     * @param key    : cache key
     */
    @Override
    public void sendEvictCmd(String region, Object key) {
        // 发送广播
        Command cmd = new Command(Command.OPT_DELETE_KEY, region, key);
        try {
            redis.publish(channel.getBytes(), cmd.toBuffers());
        } catch (Exception e) {
            log.error("Unable to delete cache,region=" + region + ",key=" + key, e);
        }
    }

    /**
     * 发送清除缓存的广播命令
     *
     * @param region: Cache region name
     */
    @Override
    public void sendClearCmd(String region) {
        // 发送广播
        Command cmd = new Command(Command.OPT_CLEAR_KEY, region, "");
        try {
            redis.publish(channel.getBytes(), cmd.toBuffers());
        } catch (Exception e) {
            log.error("Unable to clear cache,region=" + region, e);
        }
    }

    public void onMessage(byte[] channel, byte[] message) {
        // 无效消息
        if (message != null && message.length <= 0) {
            return;
        }

        try {
            Command cmd = Command.parse(message);

            if (cmd == null || cmd.isLocalCommand())
                return;

            switch (cmd.getOperator()) {
                case Command.OPT_DELETE_KEY:
                    this.evict(cmd.getRegion(), cmd.getKey());
                    log.debug("Received cache evict message, region=" + cmd.getRegion() + ",key=" + cmd.getKey());
                    break;
                case Command.OPT_CLEAR_KEY:
                    this.clear(cmd.getRegion());
                    log.debug("Received cache clear message, region=" + cmd.getRegion());
                    break;
                default:
                    log.warn("Unknown message type = " + cmd.getOperator());
            }
        } catch (Exception e) {
            log.error("Unable to handle received msg", e);
        }
    }

    @Override
    public void disconnect() {
        this.unsubscribe();
    }
}
