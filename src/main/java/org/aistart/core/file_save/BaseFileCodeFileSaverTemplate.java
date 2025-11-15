package org.aistart.core.file_save;

import cn.hutool.core.util.StrUtil;
import org.aistart.ai.model.BaseCodeResult;
import org.aistart.exception.BusinessException;
import org.aistart.exception.ErrorCode;
import org.aistart.model.enums.CodeGenTypeEnum;

public class BaseFileCodeFileSaverTemplate extends CodeFileSaverTemplate<BaseCodeResult> {
    @Override
    public CodeGenTypeEnum getCodeType() {
        return CodeGenTypeEnum.BASE;
    }

    @Override
    protected void saveFiles(BaseCodeResult result, String baseDirPath) {

        writeToFile(baseDirPath, "result.txt",
                result.getRole()+" "+result.getWorkflow()
                        +" "+result.getResult()+
                        " 输出tokens："+result.getCompletionTokens()+
                        "。消耗总tokens："+result.getTotalTokens()+
                        "。消耗总时间："+result.getProcessingTime());
    }

    @Override
    protected void validateInput(BaseCodeResult result) {
        super.validateInput(result);
        if (StrUtil.isBlank(result.getResult())) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "结果内容不能为空");
        }
    }


}
