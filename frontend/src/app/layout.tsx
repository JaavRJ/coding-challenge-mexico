import type { Metadata } from 'next'
import { JetBrains_Mono } from 'next/font/google'
import './globals.css'

const jetbrains = JetBrains_Mono({
  subsets: ['latin'],
  variable: '--font-mono',
  weight: ['400', '500', '700'],
})

export const metadata: Metadata = {
  title: "NobaTrade Institutional Engine",
  description: "High-performance cryptocurrency arbitrage and analytics engine.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={jetbrains.variable}>
      <body className="bg-zinc-950 antialiased">{children}</body>
    </html>
  )
}
