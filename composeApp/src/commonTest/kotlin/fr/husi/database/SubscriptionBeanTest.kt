package fr.husi.database

import fr.husi.SubscriptionType
import fr.husi.fmt.KryoConverters
import kotlin.test.Test
import kotlin.test.assertEquals

class SubscriptionBeanTest {

    @Test
    fun `serialization preserves age identity`() {
        val subscription = SubscriptionBean().apply {
            type = SubscriptionType.RAW
            link = "https://example.com/sub"
            ageIdentity = "AGE-SECRET-KEY-1TEST"
        }

        val restored = KryoConverters.deserialize(
            SubscriptionBean(),
            KryoConverters.serialize(subscription),
        )

        assertEquals("AGE-SECRET-KEY-1TEST", restored.ageIdentity)
    }
}
