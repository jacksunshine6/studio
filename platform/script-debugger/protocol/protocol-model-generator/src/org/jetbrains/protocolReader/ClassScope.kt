package org.jetbrains.protocolReader

abstract class ClassScope(val generator: DomainGenerator, val classContextNamespace: NamePath) {
  private val additionalMemberTexts = ArrayList<TextOutConsumer>(2)

  protected fun getShortClassName(): String {
    return classContextNamespace.lastComponent
  }

  fun addMember(out: TextOutConsumer) {
    additionalMemberTexts.add(out)
  }

  fun writeAdditionalMembers(out: TextOutput) {
    if (additionalMemberTexts.isEmpty()) {
      return
    }

    out.newLine()
    for (deferredWriter in additionalMemberTexts) {
      deferredWriter.append(out)
    }
  }

  abstract fun getTypeDirection(): TypeData.Direction

  default object {
    fun getName(named: ItemDescriptor.Named) = named.shortName() ?: named.name()
  }
}
