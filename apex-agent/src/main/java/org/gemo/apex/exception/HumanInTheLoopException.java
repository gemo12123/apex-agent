package org.gemo.apex.exception;

/**
 * Human in the Loop 异常
 * 当需要用户输入时抛出此异常，用于中断 SuperAgent 主循环执行流程
 *
 * @author gemo
 */
public class HumanInTheLoopException extends RuntimeException {

    public HumanInTheLoopException(String message) {
        super(message);
    }

    public HumanInTheLoopException(String message, Throwable cause) {
        super(message, cause);
    }
}
