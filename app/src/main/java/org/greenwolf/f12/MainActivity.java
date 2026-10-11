package org.greenwolf.f12;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.content.Context;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.webkit.*;
import android.widget.*;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.util.concurrent.atomic.AtomicInteger;

public class MainActivity extends Activity {
  LinearLayout root, browserHost, toolbar;
  WebView browser, inspector;
  EditText address;
  String dock="bottom", activeTab="Elements";
  boolean devOpen=false, preserve=false;
  final AtomicInteger requestId=new AtomicInteger();
  int requests=0;
  int px(float dp){return (int)(getResources().getDisplayMetrics().density*dp);}
  TextView label(String s,int size){
    TextView v=new TextView(this);v.setText(s);v.setTextColor(Color.WHITE);v.setTextSize(size);
    v.setGravity(Gravity.CENTER);return v;
  }
  TextView button(String s,Runnable action){
    TextView v=label(s,15);v.setPadding(px(10),px(11),px(10),px(11));
    v.setOnClickListener(w->action.run());return v;
  }
  @Override public void onCreate(Bundle state){
    super.onCreate(state);
    getWindow().setStatusBarColor(Color.BLACK);
    getWindow().setNavigationBarColor(Color.BLACK);
    browser=new WebView(this);
    browser.setBackgroundColor(Color.WHITE);
    WebSettings settings=browser.getSettings();
    settings.setJavaScriptEnabled(true);settings.setDomStorageEnabled(true);
    settings.setBuiltInZoomControls(true);settings.setDisplayZoomControls(false);
    browser.setWebViewClient(new WebViewClient(){
      @Override public void onPageStarted(WebView view,String url,android.graphics.Bitmap icon){
        address.setText(url);
        if(!preserve){ requests=0;emit("clearNetwork", ""); }
        emit("page",url);
      }
      @Override public void onPageFinished(WebView view,String url){
        address.setText(url);emit("page",url);
        if(devOpen && activeTab.equals("Elements"))snapshot();
      }
      @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest req){
        String url=req.getUrl().toString(), method=req.getMethod();
        int id=requestId.incrementAndGet();
        runOnUiThread(()->{requests++;network(id,method,url,"—");});
        return null;
      }
      @Override public void onReceivedHttpError(WebView view,WebResourceRequest req,WebResourceResponse response){
        emit("httpError",new JSONObjectString(req.getUrl().toString(),response.getStatusCode()).value);
      }
      @Override public void onReceivedError(WebView view,WebResourceRequest req,WebResourceError error){
        emit("resourceError",new JSONObjectString(req.getUrl().toString(),-1).value);
      }
    });
    browser.setWebChromeClient(new WebChromeClient(){
      @Override public boolean onConsoleMessage(ConsoleMessage m){
        emit("console",obj("level",m.messageLevel().toString(),"message",m.message(),"line",String.valueOf(m.lineNumber())));
        return true;
      }
      @Override public void onProgressChanged(WebView view,int progress){
        emit("progress",String.valueOf(progress));
      }
    });
    inspector=new WebView(this);
    inspector.setBackgroundColor(0xff0e0f11);
    inspector.getSettings().setJavaScriptEnabled(true);
    inspector.addJavascriptInterface(new Bridge(),"Wolf");
    inspector.loadUrl("file:///android_asset/devtools.html");
    buildLayout();
    browser.loadUrl("https://www.google.com/");
  }
  String obj(String... pairs){
    JSONObject j=new JSONObject();
    try {for(int i=0;i<pairs.length;i+=2)j.put(pairs[i],pairs[i+1]);}catch(Exception ignored){}
    return j.toString();
  }
  static class JSONObjectString{
    String value;
    JSONObjectString(String url,int code){
      JSONObject j=new JSONObject();
      try{j.put("url",url);j.put("status",code);}catch(Exception ignored){}
      value=j.toString();
    }
  }
  void emit(String type,String value){
    runOnUiThread(()->{
      if(inspector==null)return;
      String script="window.fromAndroid&&window.fromAndroid("+JSONObject.quote(type)+","+JSONObject.quote(value)+")";
      inspector.evaluateJavascript(script,null);
    });
  }
  void network(int id,String method,String url,String status){
    emit("network",obj("id",""+id,"method",method,"url",url,"status",status));
  }
  void buildLayout(){
    root=new LinearLayout(this);root.setOrientation(dock.equals("left")||dock.equals("right")?0:1);
    root.setBackgroundColor(0xff0d0e10);
    setContentView(root);
    browserHost=new LinearLayout(this);browserHost.setOrientation(1);
    browserHost.setBackgroundColor(0xff222428);
    toolbar=new LinearLayout(this);toolbar.setGravity(Gravity.CENTER_VERTICAL);
    toolbar.setBackgroundColor(0xff24272b);
    toolbar.addView(button("‹",()->{if(browser.canGoBack())browser.goBack();}));
    toolbar.addView(button("›",()->{if(browser.canGoForward())browser.goForward();}));
    address=new EditText(this);address.setSingleLine(true);address.setTextSize(13);
    address.setTextColor(Color.WHITE);address.setHintTextColor(0xffaeb5bd);
    address.setHint("Search Google or enter URL");
    address.setBackgroundColor(0xff383c42);
    toolbar.addView(address,new LinearLayout.LayoutParams(0,px(43),1));
    address.setOnEditorActionListener((v,a,event)->{navigate(address.getText().toString());return true;});
    toolbar.addView(button("↻",()->browser.reload()));
    toolbar.addView(button("F12",()->toggle()));
    browserHost.addView(toolbar,new LinearLayout.LayoutParams(-1,px(51)));
    if(browser.getParent()!=null)((android.view.ViewGroup)browser.getParent()).removeView(browser);
    browserHost.addView(browser,new LinearLayout.LayoutParams(-1,0,1));
    if(inspector.getParent()!=null)((android.view.ViewGroup)inspector.getParent()).removeView(inspector);
    if(!devOpen){
      root.addView(browserHost,new LinearLayout.LayoutParams(-1,-1));return;
    }
    boolean horizontal=dock.equals("left")||dock.equals("right");
    int toolSize=horizontal?getResources().getDisplayMetrics().widthPixels/2:getResources().getDisplayMetrics().heightPixels/2;
    LinearLayout.LayoutParams flex=horizontal?new LinearLayout.LayoutParams(0,-1,1):new LinearLayout.LayoutParams(-1,0,1);
    LinearLayout.LayoutParams fixed=horizontal?new LinearLayout.LayoutParams(toolSize,-1):new LinearLayout.LayoutParams(-1,toolSize);
    if(dock.equals("top")||dock.equals("left")){
      root.addView(inspector,fixed);root.addView(browserHost,flex);
    }else{
      root.addView(browserHost,flex);root.addView(inspector,fixed);
    }
  }
  void toggle(){devOpen=!devOpen;buildLayout();if(devOpen){emit("dock",dock);snapshot();}}
  void navigate(String s){
    s=s.trim();if(s.isEmpty())return;
    if(!s.matches("(?i)^https?://.*"))
      s=(s.contains(" ")||!s.contains("."))?"https://www.google.com/search?q="+Uri.encode(s):"https://"+s;
    browser.loadUrl(s);
    ((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(address.getWindowToken(),0);
  }
  void snapshot(){
    browser.evaluateJavascript(
      "(function(){try{return document.documentElement.outerHTML.slice(0,90000)}catch(e){return String(e)}})()",
      value->emit("source",decode(value)));
  }
  String decode(String s){
    try{Object o=new JSONTokener(s).nextValue();return o==JSONObject.NULL?"null":String.valueOf(o);}
    catch(Exception e){return s;}
  }
  void evaluate(String js){
    browser.evaluateJavascript("(function(){try{var r=(0,eval)("+JSONObject.quote(js)+");return r===undefined?'undefined':String(r)}catch(e){return 'ERROR: '+String(e)}})()",
      value->{emit("result",decode(value));if(activeTab.equals("Elements"))snapshot();});
  }
  class Bridge {
    @JavascriptInterface public void command(String js){
      runOnUiThread(()->{activeTab="Console";evaluate(js);});
    }
    @JavascriptInterface public void switchTab(String tab){
      runOnUiThread(()->{activeTab=tab;if(tab.equals("Elements")||tab.equals("Source"))snapshot();});
    }
    @JavascriptInterface public void setDock(String where){
      runOnUiThread(()->{if(where.equals("bottom")||where.equals("top")||where.equals("left")||where.equals("right")){
        dock=where;buildLayout();emit("dock",dock);
      }});
    }
    @JavascriptInterface public void refreshSource(){runOnUiThread(()->snapshot());}
    @JavascriptInterface public void setPreserve(boolean value){runOnUiThread(()->preserve=value);}
    @JavascriptInterface public void editNode(String selector,String property,String value){
      runOnUiThread(()->{
        String js="(function(){var e=document.querySelector("+JSONObject.quote(selector)+");if(!e)return 'Element not found';"
          +"if("+JSONObject.quote(property)+"==='textContent')e.textContent="+JSONObject.quote(value)+";"
          +"else if("+JSONObject.quote(property)+"==='outerHTML')e.outerHTML="+JSONObject.quote(value)+";"
          +"else e.setAttribute("+JSONObject.quote(property)+","+JSONObject.quote(value)+");return 'Updated'})()";
        browser.evaluateJavascript(js,r->{emit("result",decode(r));snapshot();});
      });
    }
  }
  @Override public void onBackPressed(){
    if(browser.canGoBack())browser.goBack();else super.onBackPressed();
  }
}
