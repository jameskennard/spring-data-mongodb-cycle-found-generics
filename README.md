`MongoPersistentEntityIndexResolver` reports a spurious `Found cycle` when a generic class (e.g. Selection<T>) appears at multiple levels of a document hierarchy with different type arguments. No actual cycle exists in the document structure.

Example structure:
```java
@Document
public class FruitShop {

    public Selection<FruitBasket> fruitBaskets;

}

public class FruitBasket {
    public Selection<String> fruit;
}

public class Selection<T> {

    public T include;
}

```

On startup, the index resolver logs:

```
2026-06-15T17:38:50.119+01:00  INFO 93629 --- [    Test worker] m.c.i.MongoPersistentEntityIndexResolver : Found cycle for field 'include' in type 'Selection' for path 'include -> fruit -> include'
```

`Selection<String>.include` is `List<String>` — a terminal type with no further entity graph to traverse. There is no cycle.

**Root cause:**

`CycleGuard.protect()` builds a `Path` of `PersistentProperty<?>` elements and detects a cycle when `Path.append(breadcrumb)` finds the breadcrumb already present in the path:

This uses `PersistentProperty.equals()`, which is implemented by `AbstractPersistentProperty.equals()` which in turn delegates to `Property`. This equals method only takes into account the raw type. So the `include` fields of type `Selection` are considered equal regardless of their generics - in the example these are `Selection<FruitBasket>` and `Selection<String>`.

**Impact:**
1. The false `CyclicPropertyReferenceException` causes the resolver to stop traversing that branch. Any `@Indexed`, `@CompoundIndex`, `@TextIndexed`, or `@WildcardIndexed` annotations on fields below the falsely-detected cycle will be ignored.
2. Additional noise in the logs (especially when you have something more complex than the repro)

**Expected behaviour:**
The resolver should recognise that Selection<FruitBasket> and Selection<String> are distinct types, and should only report a cycle when the same resolved parameterized type reappears (indicating genuine recursion, e.g. a tree node with List<Node> children).

**Suggested fix direction:**
The cycle detection needs to incorporate the resolved TypeInformation<?> (which I think may already be available).

**Issue:**
https://github.com/spring-projects/spring-data-mongodb/issues/5213