package org.aistart.service;
// 定义一个生成并上传网页截图的接口方法
// 方法名为generateAndUploadScreenshot
// 接收一个String类型的参数webUrl，表示需要截图的网页URL
// 返回值为String类型，可能是上传成功的URL或其他相关信息
public interface ScreenshotService {
/**
 * 通用生成并上传网页截图的方法
 * 该方法接收一个网页URL，生成该网页的截图，并将截图上传到指定位置
 *
 * @param webUrl 需要生成截图的网页URL地址
 * @return 返回处理结果，可能是上传成功的URL或其他相关信息
 */

     String generateAndUploadScreenshot(String webUrl);

}
