class ConcreteEntity(values: List[Int] = Nil) {
  def sampleMethod() { println("sample Method") }
}

object ConcreteEntity {    
  def slowInit(serialized: String): ConcreteEntity = {
    println("slowInit")
    new ConcreteEntity()
  }
}

class ProxyToEntity(serialized: String) { 
  lazy val concreteEntity: ConcreteEntity = ConcreteEntity.slowInit(serialized) 
}

object ProxyToEntity {
  implicit def proxy2concrete(proxy: ProxyToEntity) = proxy.concreteEntity
}

class ObjWithLazyVal {
  lazy val myValue = println("myValue initialized")
}