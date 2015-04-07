package org.jetbrains.protocolReader

abstract class CreateStandaloneTypeBindingVisitorBase(private val generator: DomainGenerator, protected val type: ProtocolMetaModel.StandaloneType) : TypeVisitor<StandaloneTypeBinding> {

  override fun visitString(): StandaloneTypeBinding {
    return generator.createTypedefTypeBinding(type, StandaloneTypeBinding.PredefinedTarget.STRING, generator.generator.naming.commonTypedef, null)
  }

  override fun visitInteger(): StandaloneTypeBinding {
    return generator.createTypedefTypeBinding(type, StandaloneTypeBinding.PredefinedTarget.INT, generator.generator.naming.commonTypedef, null)
  }

  override fun visitRef(refName: String): StandaloneTypeBinding {
    throw RuntimeException()
  }

  override fun visitBoolean(): StandaloneTypeBinding {
    throw RuntimeException()
  }

  override fun visitNumber(): StandaloneTypeBinding {
    return generator.createTypedefTypeBinding(type, StandaloneTypeBinding.PredefinedTarget.NUMBER, generator.generator.naming.commonTypedef, null)
  }

  override fun visitMap(): StandaloneTypeBinding {
    return generator.createTypedefTypeBinding(type, StandaloneTypeBinding.PredefinedTarget.MAP, generator.generator.naming.commonTypedef, null)
  }

  override fun visitUnknown(): StandaloneTypeBinding {
    throw RuntimeException()
  }
}
