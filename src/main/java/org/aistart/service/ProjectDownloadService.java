package org.aistart.service;

import jakarta.servlet.http.HttpServletResponse;

public interface ProjectDownloadService {
 /**
 * 下载项目为ZIP压缩文件
 *
 * @param projectPath 项目的路径，指定要下载的项目所在位置
 * @param downloadFileName 下载时保存的文件名，用户将以此名称保存下载的ZIP文件
 * @param response HTTP响应对象，用于将生成的ZIP文件流输出到客户端
 *
 * 该方法将指定路径下的项目文件打包成ZIP格式，并通过HTTP响应对象提供给用户下载。
 * 调用此方法时，需要确保项目路径存在且有读取权限，同时response对象已正确初始化。
 */
    void downloadProjectAsZip(String projectPath, String downloadFileName, HttpServletResponse response);
}
