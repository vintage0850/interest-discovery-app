export interface DomainSummary {
  domain: string;
  level: string;
  summary: string;
  evidence_breadth: 'LOW' | 'MEDIUM' | 'HIGH';
  has_new_change: boolean;
}

export interface CurrentLearning {
  insight_id: string;
  title: string;
  statement: string;
  confidence_level: string;
  domain: string;
}

export interface EvidenceGap {
  domain: string;
  message: string;
}

export interface NextAction {
  type: string;
  message: string;
}

export interface HomeResponse {
  summary: string;
  domains: DomainSummary[];
  current_learnings: CurrentLearning[];
  evidence_gaps: EvidenceGap[];
  next_action: NextAction;
}

export interface InputActionResult {
  action_id: string;
  primary_action: string;
  secondary_actions: string[];
  confidence: number;
}

export interface PostActionResult {
  type: 'NONE' | 'INSIGHT';
  insight_id?: string;
  title?: string;
  statement?: string;
  confidence_level?: string;
}

export interface InputResponse {
  raw_input_id: string;
  processing_state: string;
  action: InputActionResult;
  post_action: PostActionResult;
}

export type FeedbackType = 'ACCURATE' | 'PARTLY_ACCURATE' | 'INACCURATE' | 'UNSURE';

export interface FeedbackResponse {
  insight_id: string;
  feedback_status: string;
}
