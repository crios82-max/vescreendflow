import { Header } from '../components/Header'
import { Hero } from '../components/Hero'
import { Testimonials } from '../components/Testimonials'
import { SocialProof } from '../components/SocialProof'
import { SetupGuide } from '../components/SetupGuide'
import { Features } from '../components/Features'
import { SpeedStats } from '../components/SpeedStats'
import { Hardware } from '../components/Hardware'
import { WhoWeAre } from '../components/WhoWeAre'
import { Tutorials } from '../components/Tutorials'
import { Clients } from '../components/Clients'
import { Contact } from '../components/Contact'
import { Footer } from '../components/Footer'

export function Home() {
  return (
    <>
      <Header variant="yellow" />
      <main>
        <Hero />
        <Testimonials />
        <SocialProof />
        <SetupGuide />
        <Features />
        <SpeedStats />
        <Hardware />
        <WhoWeAre />
        <Tutorials />
        <Clients />
        <Contact />
      </main>
      <Footer />
    </>
  )
}
