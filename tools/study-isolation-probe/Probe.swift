import UIKit
import WebKit
final class SchemeHandler: NSObject, WKURLSchemeHandler {
 func webView(_ webView: WKWebView, start task: WKURLSchemeTask) {
  let url=task.request.url!
  var headers=["Content-Type":"text/html"]
  if url.path.contains("headers") { headers["Cross-Origin-Opener-Policy"]="same-origin";headers["Cross-Origin-Embedder-Policy"]="require-corp" }
  let data=try! Data(contentsOf:Bundle.main.url(forResource:"probe",withExtension:"html")!)
  task.didReceive(HTTPURLResponse(url:url,statusCode:200,httpVersion:"HTTP/1.1",headerFields:headers)!)
  task.didReceive(data);task.didFinish()
 }
 func webView(_ webView: WKWebView, stop task: WKURLSchemeTask) {}
}
final class Delegate: UIResponder, UIApplicationDelegate, WKScriptMessageHandler {
 var window:UIWindow?
 var web:WKWebView?
 var index=0
 var generation=0
 var results:[[String:Any]]=[]
 let urls=["kento://local/plain/","kento://local/headers/","http://127.0.0.1:4178/plain/","http://127.0.0.1:4178/headers/","http://127.0.0.1:4178/sw/"]
 func application(_ application:UIApplication,didFinishLaunchingWithOptions options:[UIApplication.LaunchOptionsKey:Any]?) -> Bool {
  window=UIWindow(frame:UIScreen.main.bounds);window!.rootViewController=UIViewController();window!.makeKeyAndVisible();next();return true
 }
 func next() {
  guard index<urls.count else {return}
  generation += 1;let current=generation
  let config=WKWebViewConfiguration();config.setURLSchemeHandler(SchemeHandler(),forURLScheme:"kento");config.userContentController.add(self,name:"probe")
  web=WKWebView(frame:window!.bounds,configuration:config);window!.rootViewController!.view=web!
  web!.load(URLRequest(url:URL(string:urls[index])!))
  DispatchQueue.main.asyncAfter(deadline:.now()+20){if self.generation==current{self.finish(["error":"timeout","url":self.urls[self.index]])}}
 }
 func userContentController(_ controller:WKUserContentController,didReceive message:WKScriptMessage){
  guard let value=message.body as? [String:Any] else{return};finish(value)
 }
 func finish(_ result:[String:Any]) {
  generation += 1
  var record=result
  record["systemVersion"]=UIDevice.current.systemVersion
  record["requestedURL"]=urls[index]
  results.append(record)
  let file=FileManager.default.urls(for:.documentDirectory,in:.userDomainMask)[0].appendingPathComponent("results.json")
  try! JSONSerialization.data(withJSONObject:results,options:[.prettyPrinted,.sortedKeys]).write(to:file)
  web?.stopLoading();web?.configuration.userContentController.removeScriptMessageHandler(forName:"probe");index += 1;next()
 }
}
UIApplicationMain(CommandLine.argc,CommandLine.unsafeArgv,nil,NSStringFromClass(Delegate.self))
