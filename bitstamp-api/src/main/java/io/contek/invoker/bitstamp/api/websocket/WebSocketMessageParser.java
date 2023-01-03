package io.contek.invoker.bitstamp.api.websocket;

import static io.contek.invoker.bitstamp.api.websocket.common.constants.WebSocketEventKeys._bts;
import static io.contek.invoker.bitstamp.api.websocket.common.constants.WebSocketEventKeys._data;
import static io.contek.invoker.bitstamp.api.websocket.common.constants.WebSocketEventKeys._order;
import static io.contek.invoker.bitstamp.api.websocket.common.constants.WebSocketEventKeys._trade;
import static io.contek.invoker.bitstamp.api.websocket.common.constants.WebSocketFieldKeys._channel;
import static io.contek.invoker.bitstamp.api.websocket.common.constants.WebSocketFieldKeys._event;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.contek.invoker.bitstamp.api.websocket.common.WebSocketRequestConfirmationMessage;
import io.contek.invoker.bitstamp.api.websocket.market.DiffOrderBookChannel;
import io.contek.invoker.bitstamp.api.websocket.market.LiveOrdersChannel;
import io.contek.invoker.bitstamp.api.websocket.market.LiveTradesChannel;
import io.contek.invoker.commons.websocket.AnyWebSocketMessage;
import io.contek.invoker.commons.websocket.IWebSocketComponent;
import io.contek.invoker.commons.websocket.WebSocketTextMessageParser;
import javax.annotation.concurrent.Immutable;

@Immutable
final class WebSocketMessageParser extends WebSocketTextMessageParser {

  private final Gson gson = new Gson();

  static WebSocketMessageParser getInstance() {
    return InstanceHolder.INSTANCE;
  }

  @Override
  public void register(IWebSocketComponent component) {}

  @Override
  protected AnyWebSocketMessage fromText(String text) {
    JsonElement json = gson.fromJson(text, JsonElement.class);
    if (!json.isJsonObject()) {
      throw new IllegalArgumentException(text);
    }
    JsonObject obj = json.getAsJsonObject();
    if (obj.has(_event) && obj.has(_channel)) {
      String eventValue = obj.get(_event).getAsString();
      String channelValue = obj.get(_channel).getAsString();
      if (eventValue.startsWith(_bts)) {
        return toRequestConfirmationMessage(obj);
      }
      if (eventValue.equals(_trade)) {
        if (channelValue.startsWith(LiveTradesChannel.PREFIX)) {
          return toLiveTradesMessage(obj);
        }
      }
      if (eventValue.startsWith(_order)) {
        if (channelValue.startsWith(LiveOrdersChannel.PREFIX)) {
          return toLiveOrdersMessage(obj);
        }
      }
      if (eventValue.equals(_data)) {
        if (channelValue.startsWith(DiffOrderBookChannel.PREFIX)) {
          return toDiffOrderBookMessage(obj);
        }
      }
    }
    throw new IllegalArgumentException(text);
  }

  private WebSocketRequestConfirmationMessage toRequestConfirmationMessage(JsonObject obj) {
    return gson.fromJson(obj, WebSocketRequestConfirmationMessage.class);
  }

  private DiffOrderBookChannel.Message toDiffOrderBookMessage(JsonObject obj) {
    return gson.fromJson(obj, DiffOrderBookChannel.Message.class);
  }

  private LiveTradesChannel.Message toLiveTradesMessage(JsonObject obj) {
    return gson.fromJson(obj, LiveTradesChannel.Message.class);
  }

  private LiveOrdersChannel.Message toLiveOrdersMessage(JsonObject obj) {
    return gson.fromJson(obj, LiveOrdersChannel.Message.class);
  }

  private WebSocketMessageParser() {}

  @Immutable
  private static final class InstanceHolder {

    private static final WebSocketMessageParser INSTANCE = new WebSocketMessageParser();

    private InstanceHolder() {}
  }
}
