package io.github.mattpvaughn.chronicle.injection.components


import dagger.Component
import io.github.mattpvaughn.chronicle.features.bookdetails.AudiobookDetailsFragment
import io.github.mattpvaughn.chronicle.injection.modules.AudiobookDetailsModule
import io.github.mattpvaughn.chronicle.injection.scopes.qualifiers.PerFragment

@PerFragment
@Component(dependencies = [ActivityComponent::class], modules = [AudiobookDetailsModule::class])
interface AudiobookDetailsComponent {
    fun inject(fragment: AudiobookDetailsFragment)
}

