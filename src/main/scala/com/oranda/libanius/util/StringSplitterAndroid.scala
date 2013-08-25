package com.oranda.libanius.util

import android.text.TextUtils
import java.lang.Character

class StringSplitterAndroid(_char: Character) extends StringSplitter(_char) {
  
  val splitter = new TextUtils.SimpleStringSplitter(_char)
  
  override def setString(str: String) {
    splitter.setString(str)
  }
  
  override def hasNext: Boolean = splitter.hasNext
  override def next: String = splitter.next
}