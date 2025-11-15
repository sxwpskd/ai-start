package org.aistart.core.file_save;

import cn.hutool.core.util.StrUtil;
import org.aistart.ai.model.BaseCodeResult;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.model.enums.CodeGenTypeEnum;

public class BaseFileCodeFileSaverTemplate extends CodeFileSaverTemplate<BaseCodeResult> {
    @Override
    public CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.MULTI_FILE;
    }

    @Override
    protected void saveFiles(BaseCodeResult result, String baseDirPath) {
        // 保存 HTML 文件
        writeToFile(baseDirPath, "result.txt",
                result.getRole()+" "+result.getWorkflow()
                        +" "+result.getResult());
    }

    @Override
    protected void validateInput(BaseCodeResult result) {
        super.validateInput(result);
        // 至少要有 HTML 代码，CSS 和 JS 可以为空
        if (StrUtil.isBlank(result.getResult())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "结果内容不能为空");
        }
    }


}
