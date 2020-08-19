/*
 * Copyright 2019-2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 with Mamoe Exceptions 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AFFERO GENERAL PUBLIC LICENSE version 3 with Mamoe Exceptions license that can be found via the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.console.graphical.model

import javafx.beans.property.SimpleStringProperty
import tornadofx.getValue
import tornadofx.setValue

class ConsoleInfo {

    val consoleVersionProperty = SimpleStringProperty()
    var consoleVersion by consoleVersionProperty

    val consoleBuildProperty = SimpleStringProperty()
    var consoleBuild by consoleBuildProperty

    val coreVersionProperty = SimpleStringProperty()
    var coreVersion by coreVersionProperty
}