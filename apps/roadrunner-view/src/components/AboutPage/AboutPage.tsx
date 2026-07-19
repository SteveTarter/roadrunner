import { useNavigate } from "react-router-dom";
import { MarkdownPageLayout } from "../Shared/MarkdownPageLayout";

export function AboutPage() {
  const navigate = useNavigate();

  const goHome = () => {
    navigate(`/home`);
  }

  return <MarkdownPageLayout
    title="About Roadrunner / Privacy Notice"
    markdownUrl="/about/About.md"
    onClose={goHome}
  />
}