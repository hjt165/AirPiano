<center><h1>空气钢琴——基于MediaPipe的手势演奏</h1></center>
## 一、应用分析

**同类应用（空气键盘、空气画板）：**

空气钢琴与空气键盘、空气画板同属「手势交互类应用」，核心技术均依赖计算机视觉（如MediaPipe、OpenCV）实现“无实体接触”的操作体验，目标是打破传统实体设备的限制，提供更灵活、更具趣味性的交互方式。

**音游->游戏**

基于手势交互核心（MediaPipe手部关键点检测、动作判定、音效视觉反馈），可逐步向游戏扩展，形成“休闲工具→趣味游戏→沉浸式体验”的梯度升级，不断丰富交互场景和用户体验，扩展逻辑贴合手势交互类应用的技术共性

## 二、 Hand Landmarker

**关键点**

掌根：0

拇指指尖：4

食指指尖：8

中指指尖：12

无名指指尖：16

小指指尖：20

## 三、设计思路

**指尖的高度偏移 - 掌根高度偏移 短时间增大->判断敲下**

**敲下->指尖的高度偏移 - 掌根高度偏移 - 敲下时的差值小于阈值->判断按住**

**不同手指区分音阶**

**平滑防抖**

**音源播放**

## 四、代码

```kotlin
private fun processAirpianoLogic(result: GestureRecognizerResult) {
    if (result.landmarks().isEmpty()) return
    
    val handLandmarks = result.landmarks()[0]
    if (handLandmarks.size < 21) return
    
    // 获取手掌根坐标（手腕）
    val palmBase = handLandmarks[0] // 手腕是第一个关键点
    val currentPalmBaseY = palmBase.y()
    
    // 记录掌根历史位置
    palmBaseHistory.add(currentPalmBaseY)
    if (palmBaseHistory.size > 5) {
        palmBaseHistory.removeAt(0)
    }
    
    // 手指索引：1-4是拇指，5-8是食指，9-12是中指，13-16是无名指，17-20是小指
    val fingerTips = listOf(4, 8, 12, 16, 20) // 手指尖的索引
    
    for ((index, tipIndex) in fingerTips.withIndex()) {
        val tip = handLandmarks[tipIndex]
        var currentTipY = tip.y()
        
        // 记录手指历史位置
        val tipHistory = fingerTipHistories.getOrPut(index) { mutableListOf() }
        currentTipY = smoothFingerPosition(tipIndex,currentTipY)
        // tipHistory.add(currentTipY)
        if (tipHistory.size > 5) {
            tipHistory.removeAt(0)
        }
        
        // 计算指尖短时间的偏移量（当前位置 - 历史平均位置）
        val tipOffset = if (tipHistory.size > 1) {
            val avgTipY = tipHistory.subList(0, tipHistory.size - 1).average().toFloat()
            currentTipY - avgTipY
        } else {
            0f
        }
        
        // 计算掌根短时间的偏移量（当前位置 - 历史平均位置）
        val palmOffset = if (palmBaseHistory.size > 1) {
            val avgPalmY = palmBaseHistory.subList(0, palmBaseHistory.size - 1).average().toFloat()
            currentPalmBaseY - avgPalmY
        } else {
            0f
        }
        
        // 计算差值：指尖偏移量 - 掌根偏移量
        // 对于向下敲的动作，指尖的y坐标增大速度快于掌根
        val offsetDiff = tipOffset - palmOffset
        
        // 获取手指状态
        val currentState = fingerStates.getOrPut(index) { FingerState() }
        // 更新状态
        if (offsetDiff > TAP_THRESHOLD) {
            // 检测到敲击动作（指尖向下移动速度快于掌根）
            if (!currentState.isTapped) {
                Log.d("偏移", "${offsetDiff}")
                currentState.isTapped = true
                currentState.tapOffsetDiff = offsetDiff
                // 播放音效
                playSound(index)
            }
        } else if (currentState.isTapped && Math.abs(offsetDiff - currentState.tapOffsetDiff) < HOLD_THRESHOLD) {
            // 检测到按住状态
            currentState.isHolding = true
        } else if (offsetDiff < currentState.tapOffsetDiff - TAP_THRESHOLD) {
            // 检测到抬起动作
            currentState.isTapped = false
            currentState.isHolding = false
        }
        
        // 更新之前的偏移差值
        currentState.previousOffsetDiff = offsetDiff
    }
}
```

```kotlin
private fun smoothFingerPosition(fingerIndex: Int, currentY: Float): Float {
    val history = fingerHistory.getOrPut(fingerIndex) { mutableListOf() }
    history.add(currentY)
    
    // 只保留最近的5个值
    if (history.size > 5) {
        history.removeAt(0)
    }
    
    // 计算加权平均值
    var sum = 0.0f
    var weight = 1.0f
    var totalWeight = 0.0f
    
    for (i in history.size - 1 downTo 0) {
        sum += history[i] * weight
        totalWeight += weight
        weight *= SMOOTHING_FACTOR
    }
    
    return sum / totalWeight
}
```

```kotlin
private fun setupSoundPool() {
    // 加载项目中现有的钢琴音效
    try {
        soundIds.add(MediaPlayer.create(context, R.raw.d))
        soundIds.add(MediaPlayer.create(context, R.raw.re))
        soundIds.add(MediaPlayer.create(context, R.raw.mi))
        soundIds.add(MediaPlayer.create(context, R.raw.fa))
        soundIds.add(MediaPlayer.create(context, R.raw.sol))
    } catch (e: Exception) {
        Log.e(TAG, "Error loading piano sounds", e)
    }
}

private fun playSound(fingerIndex: Int) {
    // 确保即使在音效加载失败的情况下也能正常处理
    if (soundIds.isNotEmpty()) {
        // 使用手指索引对音效数量取模，确保即使手指索引超出范围也能播放音效
        val soundIndex = fingerIndex % soundIds.size
        // 确保音效ID有效
        soundIds[soundIndex].start()
    }
}
```

## 五、升级方案

设计钢琴样式判断手指是否触发虚拟钢琴键->通过位置的思路提高音阶种类，更符合钢琴实际情况