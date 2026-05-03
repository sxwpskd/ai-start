package org.aistart.core.builder;

import cn.hutool.core.util.RuntimeUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.concurrent.TimeUnit;
/**
 *构建vue项目
 *由于window系统执行命令npm需要npm.cmd，所以需要判断当前系统，并添加后缀
 * */
@Slf4j
@Component
public class VueProjectBuilder {


        /**
         * 异步构建项目（不阻塞主流程）
         *
         * @param projectPath 项目路径
         */
        public void buildProjectAsync(String projectPath) {
            // 在单独的线程中执行构建，避免阻塞主流程，java21特性
            Thread.ofVirtual().name("vue-builder-" + System.currentTimeMillis()).start(() -> {
                try {
                    buildProject(projectPath);
                } catch (Exception e) {
                    log.error("异步构建 Vue 项目时发生异常: {}", e.getMessage(), e);
                }
            });
        }


    /**
     * 构建 Vue 项目
     *
     * @param projectPath 项目根目录路径
     * @return 是否构建成功
     */
    public boolean buildProject(String projectPath) {
        File projectDir = new File(projectPath);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            log.error("项目目录不存在: {}", projectPath);
            return false;
        }
        // 检查 package.json 是否存在
        File packageJson = new File(projectDir, "package.json");
        if (!packageJson.exists()) {
            log.error("package.json 文件不存在: {}", packageJson.getAbsolutePath());
            return false;
        }
        log.info("开始构建 Vue 项目: {}", projectPath);
        // 执行 npm install
        if (!executeNpmInstall(projectDir)) {
            log.error("npm install 执行失败");
            return false;
        }
        // 执行 npm run build
        if (!executeNpmBuild(projectDir)) {
            log.error("npm run build 执行失败");
            return false;
        }
        // 验证 dist 目录是否生成
        File distDir = new File(projectDir, "dist");
        if (!distDir.exists()) {
            log.error("构建完成但 dist 目录未生成: {}", distDir.getAbsolutePath());
            return false;
        }
        log.info("Vue 项目构建成功，dist 目录: {}", distDir.getAbsolutePath());
        return true;
    }

    /**
     * 执行 npm install 命令
     */
    private boolean executeNpmInstall(File projectDir) {
        log.info("执行 npm install...");
        String command = String.format("%s install", buildCommand("npm"));
        return executeCommand(projectDir, command, 300); // 5分钟超时
    }

    /**
     * 执行 npm run build 命令
     */
    private boolean executeNpmBuild(File projectDir) {
        log.info("执行 npm run build...");
        String command = String.format("%s run build", buildCommand("npm"));
        return executeCommand(projectDir, command, 180); // 3分钟超时
    }

    /**
 * 构建适合当前操作系统的命令
 * @param baseCommand 基础命令字符串
 * @return 返回适合当前操作系统的完整命令，如果是Windows系统则添加.cmd后缀
 */
    private String buildCommand(String baseCommand) {
    // 判断当前操作系统是否为Windows系统
        if (isWindows()) {
        // 如果是Windows系统，为命令添加.cmd后缀
            return baseCommand + ".cmd";
        }
    // 非Windows系统直接返回原命令
        return baseCommand;
    }

    /**
 * 判断当前操作系统是否为Windows系统
 * @return 如果是Windows系统返回true，否则返回false
 */
    private boolean isWindows() {
    // 获取操作系统名称并转换为小写
        return System.getProperty("os.name").toLowerCase().contains("windows");
    }


    /**
     * 执行命令
     *
     * @param workingDir     工作目录
     * @param command        命令字符串
     * @param timeoutSeconds 超时时间（秒）
     * @return 是否执行成功
     */
    private boolean executeCommand(File workingDir, String command, int timeoutSeconds) {
        try {
            // 记录执行命令的信息，包括工作目录和命令内容
            log.info("在目录 {} 中执行命令: {}", workingDir.getAbsolutePath(), command);
            // 使用RuntimeUtil执行命令，将命令字符串按空格分割成数组
            Process process = RuntimeUtil.exec(
                    null,
                    workingDir,
                    command.split("\\s+") // 命令分割为数组
            );
            // 等待进程完成，设置超时时间
            boolean finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            // 如果进程未在指定时间内完成，则记录错误并强制终止进程
            if (!finished) {
                log.error("命令执行超时（{}秒），强制终止进程", timeoutSeconds);
                process.destroyForcibly();
                return false;
            }
            // 获取进程的退出码
            int exitCode = process.exitValue();
            // 根据退出码判断命令执行是否成功
            if (exitCode == 0) {
                log.info("命令执行成功: {}", command);
                return true;
            } else {
                log.error("命令执行失败，退出码: {}", exitCode);
                return false;
            }
        } catch (Exception e) {
            // 捕获并记录执行过程中的异常信息
            log.error("执行命令失败: {}, 错误信息: {}", command, e.getMessage());
            return false;
        }
    }

}