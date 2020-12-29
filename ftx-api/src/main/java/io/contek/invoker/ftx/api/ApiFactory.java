package io.contek.invoker.ftx.api;

import com.google.common.collect.ImmutableList;
import io.contek.invoker.commons.api.ApiContext;
import io.contek.invoker.commons.api.actor.IActor;
import io.contek.invoker.commons.api.actor.IActorFactory;
import io.contek.invoker.commons.api.actor.SimpleActorFactory;
import io.contek.invoker.commons.api.actor.http.SimpleHttpClientFactory;
import io.contek.invoker.commons.api.actor.ratelimit.*;
import io.contek.invoker.commons.api.rest.RestContext;
import io.contek.invoker.commons.api.websocket.WebSocketContext;
import io.contek.invoker.ftx.api.rest.market.MarketRestApi;
import io.contek.invoker.ftx.api.rest.user.UserRestApi;
import io.contek.invoker.ftx.api.websocket.market.MarketWebSocketApi;
import io.contek.invoker.ftx.api.websocket.user.UserWebSocketApi;
import io.contek.invoker.security.ApiKey;
import io.contek.invoker.security.SimpleCredentialFactory;

import javax.annotation.Nullable;
import javax.annotation.concurrent.Immutable;
import javax.annotation.concurrent.ThreadSafe;
import java.time.Duration;

import static com.google.common.io.BaseEncoding.base16;
import static io.contek.invoker.commons.api.actor.ratelimit.RateLimitType.IP;
import static io.contek.invoker.security.SecretKeyAlgorithm.HMAC_SHA256;

@ThreadSafe
public final class ApiFactory {

  public static final ApiContext MAIN_NET_CONTEXT =
      ApiContext.newBuilder()
          .setRestContext(RestContext.forBaseUrl("https://ftx.com"))
          .setWebSocketContext(WebSocketContext.forBaseUrl("wss://ftx.com"))
          .build();

  private final ApiContext context;
  private final IActorFactory actorFactory;

  private ApiFactory(ApiContext context, IActorFactory actorFactory) {
    this.context = context;
    this.actorFactory = actorFactory;
  }

  public static ApiFactory getMainNetDefault() {
    return fromContext(MAIN_NET_CONTEXT);
  }

  public static ApiFactory fromContext(ApiContext context) {
    return new ApiFactory(context, createActorFactory(context.getInterceptor()));
  }

  public SelectingRestApi rest() {
    return new SelectingRestApi();
  }

  public SelectingWebSocketApi ws() {
    return new SelectingWebSocketApi();
  }

  private static SimpleActorFactory createActorFactory(
      @Nullable IRateLimitQuotaInterceptor interceptor) {
    return SimpleActorFactory.newBuilder()
        .setCredentialFactory(createCredentialFactory())
        .setHttpClientFactory(SimpleHttpClientFactory.getInstance())
        .setRateLimitThrottleFactory(
            SimpleRateLimitThrottleFactory.create(createRateLimitCache(), interceptor))
        .build();
  }

  private static SimpleCredentialFactory createCredentialFactory() {
    return SimpleCredentialFactory.newBuilder()
        .setAlgorithm(HMAC_SHA256)
        .setEncoding(base16().lowerCase())
        .build();
  }

  private static RateLimitCache createRateLimitCache() {
    return RateLimitCache.newBuilder().addRule(RateLimits.IP_REST_PUBLIC_REQUEST_RULE).build();
  }

  @ThreadSafe
  public final class SelectingRestApi {

    private SelectingRestApi() {}

    public MarketRestApi market() {
      RestContext restContext = context.getRestContext();
      IActor actor = actorFactory.create(null, restContext);
      return new MarketRestApi(actor, restContext);
    }

    public UserRestApi user(ApiKey apiKey) {
      RestContext restContext = context.getRestContext();
      IActor actor = actorFactory.create(apiKey, restContext);
      return new UserRestApi(actor, restContext);
    }
  }

  @ThreadSafe
  public final class SelectingWebSocketApi {

    private SelectingWebSocketApi() {}

    public MarketWebSocketApi market() {
      WebSocketContext wsContext = context.getWebSocketContext();
      IActor actor = actorFactory.create(null, wsContext);
      return new MarketWebSocketApi(actor, wsContext);
    }

    public UserWebSocketApi user(ApiKey apiKey) {
      WebSocketContext wsContext = context.getWebSocketContext();
      IActor actor = actorFactory.create(apiKey, wsContext);
      return new UserWebSocketApi(actor, wsContext);
    }
  }

  @Immutable
  public static final class RateLimits {

    public static final RateLimitRule IP_REST_PUBLIC_REQUEST_RULE =
        RateLimitRule.newBuilder()
            .setName("ip_rest_public_request_rule")
            .setType(IP)
            .setMaxPermits(30)
            .setResetPeriod(Duration.ofSeconds(1))
            .build();

    public static final ImmutableList<RateLimitQuota> ONE_REST_PUBLIC_REQUEST =
        ImmutableList.of(IP_REST_PUBLIC_REQUEST_RULE.createRateLimitQuota(1));

    private RateLimits() {}
  }
}
