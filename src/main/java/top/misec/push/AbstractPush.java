package top.misec.push;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import top.misec.config.ConfigLoader;
import top.misec.push.model.PushMetaInfo;
import top.misec.push.model.PushResult;
import top.misec.push.model.RetryContext;
import top.misec.utils.HttpUtils;

import java.net.Proxy;

/**
 * 推送抽象类；公共模板方法封装.
 *
 * @author itning
 * @since 2021/3/22 16:36
 */
@Slf4j
public abstract class AbstractPush implements Push {

    @Override
    public final PushResult doPush(PushMetaInfo metaInfo, String content) {
        String url = generatePushUrl(metaInfo);
        assert null != url : "推送URL不能为空";
        String pushContent = generatePushBody(metaInfo, content);
        JsonObject jsonObject = post(url, pushContent);
        boolean pushStatus = checkPushStatus(jsonObject);
        if (pushStatus) {
            log.info("任务状态推送成功");
            return PushResult.success();
        } else {
            log.info("任务状态推送失败，开始重试");
            return retryPush(new RetryContext(url, pushContent, metaInfo.getNumberOfRetries(), metaInfo.getRetryInterval()));
        }
    }

    private JsonObject post(String url, String content) {
        Proxy proxy = ConfigLoader.helperConfig.getPushConfig().getProxy();
        OkHttpClient client = HttpUtils.client.newBuilder()
                .proxy(proxy)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(content, HttpUtils.JSON))
                .build();

        try {
            Response response = client.newCall(request).execute();
            ResponseBody responseBody = response.body();
            if (null == responseBody) {
                return null;
            }
            String result = responseBody.string();
            return new Gson().fromJson(result, JsonObject.class);
        } catch (Exception e) {
            log.error("", e);
            return null;
        }
    }

    /**
     * 重试推送
     *
     * @param context 重试上下文
     */
    private PushResult retryPush(RetryContext context) {
        while (context.next()) {
            JsonObject jsonObject = HttpUtils.doPost(context.getUrl(), context.getBody());
            boolean pushStatus = checkPushStatus(jsonObject);
            if (pushStatus) {
                log.info("任务状态推送成功");
                return PushResult.success();
            } else {
                log.info("任务状态推送失败，开始第{}次重试", context.getRetryCount());
                log.info("错误信息: {}", jsonObject);
            }
        }
        return PushResult.failed();
    }

    /**
     * 生成推送URL
     *
     * @param metaInfo 元信息
     * @return URL字符串
     */
    protected abstract String generatePushUrl(final PushMetaInfo metaInfo);

    /**
     * 检查推送结果
     *
     * @param jsonObject HTTP结果，可能为<code>null</code>
     * @return 推送成功，返回<code>true</code>
     */
    protected abstract boolean checkPushStatus(final JsonObject jsonObject);

    /**
     * 生成要推送的内容信息
     *
     * @param metaInfo 元信息
     * @param content  要推送的内容
     * @return 整理后的推送内容
     */
    protected String generatePushBody(final PushMetaInfo metaInfo, final String content) {
        return content;
    }
}
