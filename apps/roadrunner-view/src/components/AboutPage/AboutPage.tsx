import { useNavigate } from "react-router-dom";
import { MarkdownPageLayout } from "../Shared/MarkdownPageLayout";

export function AboutPage() {
  const navigate = useNavigate();

  const goHome = () => {
    const provider = localStorage.getItem('roadrunner_map_provider') || 'google';
    navigate(provider === 'google' ? '/google/home' : '/home');
  }

  return <MarkdownPageLayout
    title="About Roadrunner / Privacy Notice"
    markdownUrl="/about/About.md"
    onClose={goHome}
  />
}