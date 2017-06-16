package com.duckduckgo.mobile.android.duckduckgo.data.tab;

import com.duckduckgo.mobile.android.duckduckgo.data.base.JsonEntity;
import com.duckduckgo.mobile.android.duckduckgo.domain.tab.Tab;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by fgei on 6/14/17.
 */

public class TabJsonEntity implements Tab, JsonEntity {

    private String id;
    private String title;
    private String currentUrl;
    private boolean canGoBack;
    private boolean canGoForward;

    public TabJsonEntity() {
    }

    public TabJsonEntity(Tab tab) {
        id = tab.getId();
        title = tab.getTitle();
        currentUrl = tab.getCurrentUrl();
        canGoBack = tab.canGoBack();
        canGoForward = tab.canGoForward();
    }

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @Override
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    @Override
    public String getCurrentUrl() {
        return currentUrl;
    }

    public void setCurrentUrl(String currentUrl) {
        this.currentUrl = currentUrl;
    }

    @Override
    public boolean canGoBack() {
        return canGoBack;
    }

    public void setCanGoBack(boolean canGoBack) {
        this.canGoBack = canGoBack;
    }

    @Override
    public boolean canGoForward() {
        return canGoForward;
    }

    public void setCanGoForward(boolean canGoForward) {
        this.canGoForward = canGoForward;
    }

    private static final String KEY_ID = "id";
    private static final String KEY_TITLE = "title";
    private static final String KEY_CURRENT_URL = "current_url";
    private static final String KEY_CAN_GO_BACK = "can_go_back";
    private static final String KEY_CAN_GO_FORWARD = "can_go_forward";

    @Override
    public String toJson() {
        String json = "";
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put(KEY_ID, id);
            jsonObject.put(KEY_TITLE, title);
            jsonObject.put(KEY_CURRENT_URL, currentUrl);
            jsonObject.put(KEY_CAN_GO_BACK, canGoBack);
            jsonObject.put(KEY_CAN_GO_FORWARD, canGoForward);
            json = jsonObject.toString();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }

    @Override
    public void fromJson(String json) {
        try {
            JSONObject jsonObject = new JSONObject(json);
            id = jsonObject.getString(KEY_ID);
            title = jsonObject.getString(KEY_TITLE);
            currentUrl = jsonObject.getString(KEY_CURRENT_URL);
            canGoBack = jsonObject.getBoolean(KEY_CAN_GO_BACK);
            canGoForward = jsonObject.getBoolean(KEY_CAN_GO_FORWARD);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getKey() {
        return id;
    }
}
